/*
 * Copyright 2011 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.codenarc.rule.groovyism

import org.apache.log4j.Logger
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codenarc.rule.AbstractAstVisitor
import org.codenarc.rule.AbstractAstVisitorRule
import org.codenarc.util.AstUtil

/**
 * Instead of nested collect{}-calls use collectNested{}
 *
 * @author Joachim Baumann
 */
class UseCollectNestedRule extends AbstractAstVisitorRule {
    String name = 'UseCollectNested'
    int priority = 2
    Class astVisitorClass = UseCollectNestedAstVisitor

    protected static final String MESSAGE = 'Instead of nested collect{}-calls use collectNested{}'
}

class UseCollectNestedAstVisitor extends AbstractAstVisitor {
    private static final LOG = Logger.getLogger(UseCollectNestedAstVisitor)

    private final Stack<Parameter> parameterStack = []

    @Override
    protected void visitClassComplete(ClassNode cn) {
        if (parameterStack.size() != 0) {
            LOG.warn("Internal Error for ${cn.name}: Visits are unbalanced")
        }
    }

    @Override
    void visitMethodCallExpression(MethodCallExpression call) {
        boolean isCollectCall = false
        Parameter param
        Parameter it = new Parameter(ClassHelper.OBJECT_TYPE, 'it')
        
        /*
         The idea for this rule is to add the parameter of the
         closure used in the collect call to the stack of parameters,
         and check for each collect expression whether it is called on
         the parameter on the top of the stack
         */
        if(AstUtil.isMethodCall(call, 'collect', 1..2)) {
            isCollectCall = true

            // Extract the parameter of the provided closure for subsequent calls
            int arity = AstUtil.getMethodArguments(call).size()
            ClosureExpression ce
            if(arity == 1) {
                // closure is the first parameter
                ce = call.arguments.expressions[0]
            } else {
                // closure is second parameter
                ce = call.arguments.expressions[1]
            }
            if(ce.parameters.size() != 0) {
                // we assume correct syntax and thus only one parameter
                param = ce.parameters[0]
            }
            else {
                // implicit parameter, we use our own parameter object as placeholder
                param = it
            }

            // Now if the call is to the parameter of the closure then the node on
            // which collect is called has to be a VariableExpression
            if( call.objectExpression instanceof VariableExpression
                && !parameterStack.empty()
                && parameterStack.peek().name == call.objectExpression.name) {
                    addViolation(call, UseCollectNestedRule.MESSAGE)
            }
        }

        // add argument list
        if(isCollectCall) {
            parameterStack.push(param)
        }

        super.visitMethodCallExpression(call)

        // remove argument list
        if(isCollectCall) {
            parameterStack.pop()
        }
    }

}