// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.agent;

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.ui.impl.watch.FieldDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.openapi.project.Project;
import com.sun.jdi.Field;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MemoryAgentFieldReferringObject extends MemoryAgentReferringObject {
  @NotNull private final Field field;

  public MemoryAgentFieldReferringObject(@NotNull ObjectReference reference,
                                         boolean isWeakSoftReachable,
                                         @NotNull Field field) {
    super(reference, isWeakSoftReachable);
    this.field = field;
  }

  @NotNull
  @Override
  public ValueDescriptorImpl createValueDescription(@NotNull Project project, @NotNull Value referee) {
    return new FieldDescriptorImpl(project, reference, field) {
      @Override
      public Value calcValue(EvaluationContextImpl evaluationContext) {
        return reference;
      }
    };
  }

  @NotNull
  @Override
  public String getSeparator() { return " in "; }

  @Nullable
  @Override
  public String getNodeName(int order) {
    return null;
  }
}
