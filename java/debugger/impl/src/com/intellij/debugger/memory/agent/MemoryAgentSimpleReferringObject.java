// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.memory.agent;

import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiExpression;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;

public abstract class MemoryAgentSimpleReferringObject extends MemoryAgentReferringObject {
  public MemoryAgentSimpleReferringObject(@NotNull ObjectReference reference,
                                          boolean isWeakSoftReachable) {
    super(reference, isWeakSoftReachable);
  }

  @Override
  public final @NotNull ValueDescriptorImpl createValueDescription(@NotNull Project project, @NotNull Value referee) {
    return new ValueDescriptorImpl(project, myReference) {
      @Override
      public Value calcValue(EvaluationContextImpl evaluationContext) {
        return getValue();
      }

      @Override
      public String getName() {
        return JavaDebuggerBundle.message("ref");
      }

      @Override
      public PsiExpression getDescriptorEvaluation(DebuggerContext context) {
        return null;
      }
    };
  }
}
