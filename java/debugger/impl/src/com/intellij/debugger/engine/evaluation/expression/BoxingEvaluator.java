// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.JVMNameUtil;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.impl.PsiJavaParserFacadeImpl;
import com.sun.jdi.*;

import java.util.Collections;
import java.util.List;

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

    Method finalMethod = method;
    List<PrimitiveValue> args = Collections.singletonList(value);
    return context.computeAndKeep(() -> process.invokeMethod(context, wrapperClass, finalMethod, args));
  }
}
