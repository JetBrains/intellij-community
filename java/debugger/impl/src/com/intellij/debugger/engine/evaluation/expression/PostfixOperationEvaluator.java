/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.psi.TokenTypeEx;
import com.intellij.psi.tree.IElementType;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NonNls;

/**
 * @author lex
 */
public class PostfixOperationEvaluator implements Evaluator{
  private final Evaluator myOperandEvaluator;
  private static final @NonNls Evaluator myRightEvaluator = new LiteralEvaluator(new Integer(1), "byte");

  private final IElementType myOpType;
  private final String myExpectedType; // a result of PsiType.getCanonicalText()

  private Modifier myModifier;

  public PostfixOperationEvaluator(Evaluator operandEvaluator, IElementType opType, String expectedType) {
    myOperandEvaluator = operandEvaluator;
    myOpType = opType;
    myExpectedType = expectedType;
  }

  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    final Object value = myOperandEvaluator.evaluate(context);
    myModifier = myOperandEvaluator.getModifier();
    IElementType opType = myOpType == TokenTypeEx.PLUSPLUS ? TokenTypeEx.PLUS : TokenTypeEx.MINUS;
    Object operationResult = BinaryExpressionEvaluator.evaluateOperation((Value)value, opType, myRightEvaluator, myExpectedType, context);
    AssignmentEvaluator.assign(myModifier, operationResult, context);
    return value;
  }

  public Modifier getModifier() {
    return myModifier;
  }
}
