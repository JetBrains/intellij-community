// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine;

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.xdebugger.frame.XFullValueEvaluator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface FullValueEvaluatorProvider {
  @Nullable
  XFullValueEvaluator getFullValueEvaluator(@NotNull EvaluationContextImpl evaluationContext, @NotNull ValueDescriptorImpl valueDescriptor);
}
