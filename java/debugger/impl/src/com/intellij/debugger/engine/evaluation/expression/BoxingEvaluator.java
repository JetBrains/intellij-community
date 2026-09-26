// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.JVMNameUtil;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.lang.jvm.types.JvmPrimitiveTypeKind;
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
      JvmPrimitiveTypeKind primitiveType = JvmPrimitiveTypeKind.getKindByName(primitiveValue.type().name());
      if (primitiveType != null && primitiveType != JvmPrimitiveTypeKind.VOID) {
        return convertToWrapper(context, primitiveValue, primitiveType);
      }
    }
    return value;
  }

  private static Value convertToWrapper(EvaluationContextImpl context,
                                        PrimitiveValue value,
                                        JvmPrimitiveTypeKind primitiveType) throws EvaluateException {
    final DebugProcessImpl process = context.getDebugProcess();
    String wrapperTypeName = primitiveType.getBoxedFqn();
    final ClassType wrapperClass = (ClassType)process.findClass(context, wrapperTypeName, null);
    String parameterSignature = "(" + primitiveType.getBinaryName() + ")";
    String methodSignature = parameterSignature + "L" + wrapperTypeName.replace('.', '/') + ";";

    Method method = DebuggerUtils.findMethod(wrapperClass, "valueOf", methodSignature);
    if (method == null) { // older JDK version
      method = DebuggerUtils.findMethod(wrapperClass, JVMNameUtil.CONSTRUCTOR_NAME, parameterSignature + "V");
    }
    if (method == null) {
      throw new EvaluateException("Cannot construct wrapper object for value of type " + value.type() + ": Unable to find either valueOf() or constructor method");
    }

    Method finalMethod = method;
    List<PrimitiveValue> args = Collections.singletonList(value);
    return context.computeAndKeep(() -> process.invokeMethod(context, wrapperClass, finalMethod, args, true));
  }
}
