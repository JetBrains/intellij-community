// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.agent;

import com.intellij.debugger.DebuggerContext;
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

  @NotNull
  @Override
  public final ValueDescriptorImpl createValueDescription(@NotNull Project project, @NotNull Value referee) {
    return new ValueDescriptorImpl(project, myReference) {
      @Override
      public Value calcValue(EvaluationContextImpl evaluationContext) {
        return getValue();
      }

      @Override
      public String getName() {
        return "Ref";
      }

      @Override
      public PsiExpression getDescriptorEvaluation(DebuggerContext context) {
        return null;
      }
    };
  }
}
