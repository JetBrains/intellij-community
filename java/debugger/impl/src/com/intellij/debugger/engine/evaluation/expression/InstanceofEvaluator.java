/*
 * Class InstanceofEvaluator
 * @author Jeka
 */
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.DebuggerBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiType;
import com.sun.jdi.*;

import java.util.LinkedList;
import java.util.List;

class InstanceofEvaluator implements Evaluator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.engine.evaluation.expression.InstanceofEvaluator");
  private final Evaluator myOperandEvaluator;
  private final TypeEvaluator myTypeEvaluator;

  public InstanceofEvaluator(Evaluator operandEvaluator, TypeEvaluator typeEvaluator) {
    myOperandEvaluator = operandEvaluator;
    myTypeEvaluator = typeEvaluator;
  }

  public Modifier getModifier() {
    return null;
  }

  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    Value value = (Value)myOperandEvaluator.evaluate(context);
    if (value == null) {
      return DebuggerUtilsEx.createValue(context.getDebugProcess().getVirtualMachineProxy(), PsiType.BOOLEAN.getPresentableText(), false);
    }
    if (!(value instanceof ObjectReference)) {
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.object.reference.expected"));
    }
    try {
      ReferenceType refType = (ReferenceType)myTypeEvaluator.evaluate(context);
      ClassObjectReference classObject = refType.classObject();
      ClassType classRefType = (ClassType)classObject.referenceType();
      //noinspection HardCodedStringLiteral
      Method method = classRefType.concreteMethodByName("isAssignableFrom", "(Ljava/lang/Class;)Z");
      List args = new LinkedList();
      args.add(((ObjectReference)value).referenceType().classObject());
      return context.getDebugProcess().invokeMethod(context, classObject, method, args);
    }
    catch (Exception e) {
      if (LOG.isDebugEnabled()) {
        LOG.debug(e);
      }
      throw EvaluateExceptionUtil.createEvaluateException(e);
    }
  }
}
