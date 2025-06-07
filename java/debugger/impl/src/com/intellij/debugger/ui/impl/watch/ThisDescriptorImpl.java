// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.impl.watch;

import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.util.IncorrectOperationException;
import com.sun.jdi.Value;

public class ThisDescriptorImpl extends ValueDescriptorImpl {

  public ThisDescriptorImpl(Project project) {
    super(project);
  }

  @Override
  public Value calcValue(EvaluationContextImpl evaluationContext) throws EvaluateException {
    return evaluationContext != null ? evaluationContext.computeThisObject() : null;
  }

  @SuppressWarnings("HardCodedStringLiteral")
  @Override
  public String getName() {
    return "this";
  }

  @Override
  public PsiExpression getDescriptorEvaluation(DebuggerContext context) throws EvaluateException {
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myProject);
    try {
      return elementFactory.createExpressionFromText("this", null);
    }
    catch (IncorrectOperationException e) {
      throw new EvaluateException(e.getMessage(), e);
    }
  }

  @Override
  public boolean canSetValue() {
    return false;
  }
}
