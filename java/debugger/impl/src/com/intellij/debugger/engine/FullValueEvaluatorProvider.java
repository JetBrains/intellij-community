// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine;

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.xdebugger.frame.XFullValueEvaluator;
import com.intellij.xdebugger.frame.XValueNode;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface FullValueEvaluatorProvider {
  @ApiStatus.OverrideOnly
  @Nullable
  XFullValueEvaluator getFullValueEvaluator(@NotNull EvaluationContextImpl evaluationContext, @NotNull ValueDescriptorImpl valueDescriptor);

  @ApiStatus.OverrideOnly
  @ApiStatus.Experimental
  default @Nullable XFullValueEvaluator getFullValueEvaluator(
    @NotNull XValueNode node,
    @NotNull EvaluationContextImpl evaluationContext,
    @NotNull ValueDescriptorImpl valueDescriptor
  ) {
    return getFullValueEvaluator(evaluationContext, valueDescriptor);
  }
}
