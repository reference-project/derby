/*

   Derby - Class org.apache.derby.impl.sql.compile.UserAggregateDefinition

   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to you under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

 */

package org.apache.derby.impl.sql.compile;

import java.lang.reflect.Method;

import org.apache.derby.iapi.sql.conn.LanguageConnectionContext;
import org.apache.derby.iapi.reference.SQLState;
import org.apache.derby.iapi.services.context.ContextService;
import org.apache.derby.iapi.services.loader.ClassFactory;
import org.apache.derby.iapi.services.sanity.SanityManager;

import org.apache.derby.catalog.TypeDescriptor;
import org.apache.derby.catalog.types.AggregateAliasInfo;
import org.apache.derby.iapi.types.TypeId;
import org.apache.derby.iapi.types.JSQLType;
import org.apache.derby.iapi.types.DataTypeDescriptor;

import org.apache.derby.iapi.sql.compile.TypeCompiler;
import org.apache.derby.iapi.sql.compile.TypeCompilerFactory;

import org.apache.derby.iapi.sql.compile.CompilerContext;
import org.apache.derby.iapi.sql.dictionary.AliasDescriptor;

import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.reference.ClassName;

/**
 * Definition for user-defined aggregates.
 *
 */
public class UserAggregateDefinition implements AggregateDefinition 
{
    ///////////////////////////////////////////////////////////////////////////////////
    //
    // CONSTANTS
    //
    ///////////////////////////////////////////////////////////////////////////////////

    // the Aggregator interface has 3 parameter types
    private static  final   int INPUT_TYPE = 0;
    private static  final   int RETURN_TYPE = INPUT_TYPE + 1;
    private static  final   int AGGREGATOR_TYPE = RETURN_TYPE + 1;
    private static  final   int AGGREGATOR_PARAM_COUNT = AGGREGATOR_TYPE + 1;

    ///////////////////////////////////////////////////////////////////////////////////
    //
    // STATE
    //
    ///////////////////////////////////////////////////////////////////////////////////

    private AliasDescriptor _alias;

    ///////////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTOR
    //
    ///////////////////////////////////////////////////////////////////////////////////

	/**
	 * Conjure out of thin air.
	 */
	public UserAggregateDefinition( AliasDescriptor alias )
    {
        _alias = alias;
    }

    ///////////////////////////////////////////////////////////////////////////////////
    //
    // BEHAVIOR
    //
    ///////////////////////////////////////////////////////////////////////////////////

    /** Get the wrapped alias descriptor */
    public  AliasDescriptor getAliasDescriptor() { return _alias; }

	/**
	 * Determines the result datatype and verifies that the input datatype is correct.
	 *
	 * @param inputType	the input type
	 * @param aggregatorClass (Output arg) the name of the Derby execution-time class which wraps the aggregate logic
	 *
	 * @return the result type of the user-defined aggregator
	 */
	public final DataTypeDescriptor	getAggregator
        ( DataTypeDescriptor inputType, StringBuffer aggregatorClass )
        throws StandardException
	{
		try
		{
			TypeId compType = inputType.getTypeId();
		
			CompilerContext cc = (CompilerContext)
				ContextService.getContext(CompilerContext.CONTEXT_ID);
			TypeCompilerFactory tcf = cc.getTypeCompilerFactory();
			TypeCompiler tc = tcf.getTypeCompiler(compType);
            ClassFactory    classFactory = cc.getClassFactory();

            Class   userAggregatorClass = classFactory.loadApplicationClass( _alias.getJavaClassName() );
            Class   derbyAggregatorInterface = classFactory.loadApplicationClass( "org.apache.derby.agg.Aggregator" );

            Class[] aggregatorTypes = classFactory.getClassInspector().getGenericParameterTypes
                ( derbyAggregatorInterface, userAggregatorClass );

            if (
                !derbyAggregatorInterface.isAssignableFrom( userAggregatorClass ) ||
                (aggregatorTypes == null) ||
                (aggregatorTypes.length != AGGREGATOR_PARAM_COUNT) ||
                (aggregatorTypes[ INPUT_TYPE ] == null) ||
                (aggregatorTypes[ RETURN_TYPE ] == null)
               )
            {
				throw StandardException.newException
                    (
                     SQLState.LANG_ILLEGAL_UDA_CLASS,
                     _alias.getSchemaName(),
                     _alias.getName(),
                     _alias.getJavaClassName()
                     );
            }

            Class   actualInputClass = aggregatorTypes[ INPUT_TYPE ];
            Class   actualReturnClass = aggregatorTypes[ RETURN_TYPE ];

            AggregateAliasInfo  aai = (AggregateAliasInfo) _alias.getAliasInfo();
            DataTypeDescriptor  expectedInputType = DataTypeDescriptor.getType( aai.getForType() );
            DataTypeDescriptor  expectedReturnType = DataTypeDescriptor.getType( aai.getReturnType() );
            Class       expectedInputClass = getJavaClass( expectedInputType );
            Class       expectedReturnClass = getJavaClass( expectedReturnType );

            // check that the aggregator has the correct input and return types
            if ( actualInputClass != expectedInputClass )
            {
				throw StandardException.newException
                    (
                     SQLState.LANG_UDA_WRONG_INPUT_TYPE,
                     _alias.getSchemaName(),
                     _alias.getName(),
                     expectedInputClass.toString(),
                     actualInputClass.toString()
                     );
            }
		
            if ( actualReturnClass != expectedReturnClass )
            {
				throw StandardException.newException
                    (
                     SQLState.LANG_UDA_WRONG_RETURN_TYPE,
                     _alias.getSchemaName(),
                     _alias.getName(),
                     expectedReturnClass.toString(),
                     actualReturnClass.toString()
                     );
            }

            aggregatorClass.append( ClassName.UserDefinedAggregator );

            return expectedReturnType;
		}
		catch (ClassNotFoundException cnfe) { throw aggregatorInstantiation( cnfe ); }
	}

    /**
     * Get the Java class corresponding to a Derby datatype.
     */
    private Class   getJavaClass( DataTypeDescriptor dtd )
        throws StandardException, ClassNotFoundException
    {
        JSQLType    jsqlType = new JSQLType( dtd );
        String  javaClassName = MethodCallNode.getObjectTypeName( jsqlType, null );

        return Class.forName( javaClassName );
    }

    /**
     * Make a "Could not instantiate aggregator" exception.
     */
    private StandardException   aggregatorInstantiation( Throwable t )
        throws StandardException
    {
        return StandardException.newException
            (
             SQLState.LANG_UDA_INSTANTIATION,
             t,
             _alias.getJavaClassName(),
             _alias.getSchemaName(),
             _alias.getName(),
             t.getMessage()
             );
    }
    
}
