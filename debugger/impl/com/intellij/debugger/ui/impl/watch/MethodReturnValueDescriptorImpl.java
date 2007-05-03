/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.ui.impl.watch;

import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiExpression;
import com.sun.jdi.ClassNotLoadedException;
import com.sun.jdi.Method;
import com.sun.jdi.Type;
import com.sun.jdi.Value;

/**
 * User: lex
 * Date: Oct 8, 2003
 * Time: 5:08:07 PM
 */
public class MethodReturnValueDescriptorImpl extends ValueDescriptorImpl{
  private final Method myMethod;
  private final Value myValue;

  public MethodReturnValueDescriptorImpl(Project project, final Method method, Value value) {
    super(project);
    myMethod = method;
    myValue = value;
  }

  public Value calcValue(EvaluationContextImpl evaluationContext) throws EvaluateException {
    return myValue;
  }

  public String getName() {
    //noinspection HardCodedStringLiteral
    return myMethod.toString();
  }

  public String calcValueName() {
    return getName();
  }

  public Type getType() {
    if (myValue == null) {
      try {
        return myMethod.returnType();
      }
      catch (ClassNotLoadedException ignored) {
      }
    }
    return super.getType();
  }

  public PsiExpression getDescriptorEvaluation(DebuggerContext context) throws EvaluateException {
    throw new EvaluateException("Evaluation not supported for method return value");
  }

  protected String calcRepresentation(EvaluationContextImpl context, DescriptorLabelListener labelListener) {
    return super.calcRepresentation(context, labelListener);
  }

  public boolean canSetValue() {
    return false;
  }
}
