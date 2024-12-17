// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.impl.watch;

import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiExpression;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;

public class ThrownExceptionValueDescriptorImpl extends ValueDescriptorImpl {
  public ThrownExceptionValueDescriptorImpl(Project project, @NotNull ObjectReference exceptionObj) {
    super(project, exceptionObj);
    // deliberately force default renderer as it does not invoke methods for rendering
    // calling methods on exception object at this moment may lead to VM hang
    setRenderer(DebugProcessImpl.getDefaultRenderer(exceptionObj));
  }

  @Override
  public Value calcValue(EvaluationContextImpl evaluationContext) throws EvaluateException {
    return getValue();
  }

  @Override
  public String getName() {
    return JavaDebuggerBundle.message("exception");
  }

  @Override
  public PsiExpression getDescriptorEvaluation(DebuggerContext context) throws EvaluateException {
    throw new NeedMarkException((ObjectReference)getValue());
  }

  @Override
  public boolean canSetValue() {
    return false;
  }
}
