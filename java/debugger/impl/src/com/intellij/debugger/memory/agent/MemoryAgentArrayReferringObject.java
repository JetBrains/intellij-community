// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.memory.agent;

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.ui.impl.watch.ArrayElementDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.openapi.project.Project;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MemoryAgentArrayReferringObject extends MemoryAgentReferringObject {
  private final int myIndex;

  public MemoryAgentArrayReferringObject(@NotNull ArrayReference reference,
                                         boolean isWeakSoftReachable,
                                         int index) {
    super(reference, isWeakSoftReachable);
    this.myIndex = index;
  }

  @Override
  public @NotNull ValueDescriptorImpl createValueDescription(@NotNull Project project, @NotNull Value referee) {
    return new ArrayElementDescriptorImpl(project, (ArrayReference)myReference, myIndex) {
      @Override
      public Value calcValue(EvaluationContextImpl evaluationContext) {
        return myReference;
      }
    };
  }

  @Override
  public @Nullable String getNodeName(int order) {
    return null;
  }

  @Override
  public @NotNull String getSeparator() { return " in "; }
}
