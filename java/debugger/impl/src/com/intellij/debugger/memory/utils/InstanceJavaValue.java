// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.utils;

import com.intellij.debugger.engine.JavaValue;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.ui.impl.watch.NodeManagerImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import org.jetbrains.annotations.NotNull;

public class InstanceJavaValue extends JavaValue {
  public InstanceJavaValue(@NotNull ValueDescriptorImpl valueDescriptor,
                           @NotNull EvaluationContextImpl evaluationContext,
                           NodeManagerImpl nodeManager) {
    super(null, valueDescriptor, evaluationContext, nodeManager, false);
  }

  @Override
  public boolean canNavigateToSource() {
    return false;
  }
}
