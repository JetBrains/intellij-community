// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.JVMNameUtil;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.impl.PsiJavaParserFacadeImpl;
import com.sun.jdi.ClassType;
import com.sun.jdi.Method;
import com.sun.jdi.PrimitiveValue;
import com.sun.jdi.Value;

import java.util.Collections;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 */
public class BoxingEvaluator implements Evaluator {
  private final Evaluator myOperand;

  public BoxingEvaluator(Evaluator operand) {
    myOperand = DisableGC.create(operand);
  }

  @Override
  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    return box(myOperand.evaluate(context), context);
  }

  public static Object box(Object value, EvaluationContextImpl context) throws EvaluateException {
    if (value instanceof PrimitiveValue primitiveValue) {
      PsiPrimitiveType primitiveType = PsiJavaParserFacadeImpl.getPrimitiveType(primitiveValue.type().name());
      if (primitiveType != null) {
        return convertToWrapper(context, primitiveValue, primitiveType.getBoxedTypeName());
      }
    }
    return value;
  }

  private static Value convertToWrapper(EvaluationContextImpl context, PrimitiveValue value, String wrapperTypeName) throws
                                                                                                                     EvaluateException {
    final DebugProcessImpl process = context.getDebugProcess();
    final ClassType wrapperClass = (ClassType)process.findClass(context, wrapperTypeName, null);
    final String methodSignature = "(" + JVMNameUtil.getPrimitiveSignature(value.type().name()) + ")L" + wrapperTypeName.replace('.', '/') + ";";

    Method method = DebuggerUtils.findMethod(wrapperClass, "valueOf", methodSignature);
    if (method == null) { // older JDK version
      method = DebuggerUtils.findMethod(wrapperClass, JVMNameUtil.CONSTRUCTOR_NAME, methodSignature);
    }
    if (method == null) {
      throw new EvaluateException("Cannot construct wrapper object for value of type " + value.type() + ": Unable to find either valueOf() or constructor method");
    }

    Method finalMethod = method;
    List<PrimitiveValue> args = Collections.singletonList(value);
    return context.computeAndKeep(() -> process.invokeMethod(context, wrapperClass, finalMethod, args, true));
  }
}
