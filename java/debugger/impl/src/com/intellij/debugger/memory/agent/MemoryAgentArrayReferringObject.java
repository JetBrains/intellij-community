// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @NotNull
  @Override
  public ValueDescriptorImpl createValueDescription(@NotNull Project project, @NotNull Value referee) {
    return new ArrayElementDescriptorImpl(project, (ArrayReference)myReference, myIndex) {
      @Override
      public Value calcValue(EvaluationContextImpl evaluationContext) {
        return myReference;
      }
    };
  }

  @Nullable
  @Override
  public String getNodeName(int order) {
    return null;
  }

  @NotNull
  @Override
  public String getSeparator() { return " in "; }
}
