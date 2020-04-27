// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine;

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.xdebugger.frame.XFullValueEvaluator;
import org.jetbrains.annotations.Nullable;

public interface FullValueEvaluatorProvider {
  @Nullable
  XFullValueEvaluator getFullValueEvaluator(EvaluationContextImpl evaluationContext, ValueDescriptorImpl valueDescriptor);
}
