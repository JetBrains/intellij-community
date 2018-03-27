/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.JVMNameUtil;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.impl.PsiJavaParserFacadeImpl;
import com.sun.jdi.*;

import java.util.Collections;

/**
 * @author Eugene Zhuravlev
 */
public class BoxingEvaluator implements Evaluator{
  private final Evaluator myOperand;

  public BoxingEvaluator(Evaluator operand) {
    myOperand = DisableGC.create(operand);
  }

  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    final Object result = myOperand.evaluate(context);
    if (result == null || result instanceof ObjectReference) {
      return result;
    }

    if (result instanceof PrimitiveValue) {
      PrimitiveValue primitiveValue = (PrimitiveValue)result;
      PsiPrimitiveType primitiveType = PsiJavaParserFacadeImpl.getPrimitiveType(primitiveValue.type().name());
      if (primitiveType != null) {
        return convertToWrapper(context, primitiveValue, primitiveType.getBoxedTypeName());
      }
    }
    throw new EvaluateException("Cannot perform boxing conversion for a value of type " + ((Value)result).type().name());
  }

  private static Value convertToWrapper(EvaluationContextImpl context, PrimitiveValue value, String wrapperTypeName) throws
                                                                                                                            EvaluateException {
    final DebugProcessImpl process = context.getDebugProcess();
    final ClassType wrapperClass = (ClassType)process.findClass(context, wrapperTypeName, null);
    final String methodSignature = "(" + JVMNameUtil.getPrimitiveSignature(value.type().name()) + ")L" + wrapperTypeName.replace('.', '/') + ";";

    Method method = wrapperClass.concreteMethodByName("valueOf", methodSignature);
    if (method == null) { // older JDK version
      method = wrapperClass.concreteMethodByName(JVMNameUtil.CONSTRUCTOR_NAME, methodSignature);
    }
    if (method == null) {
      throw new EvaluateException("Cannot construct wrapper object for value of type " + value.type() + ": Unable to find either valueOf() or constructor method");
    }

    return process.invokeMethod(context, wrapperClass, method, Collections.singletonList(value));
  }
}
