// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine;

import com.intellij.debugger.PositionManager;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.util.ThreeState;
import com.sun.jdi.Location;
import org.jetbrains.annotations.NotNull;

public interface PositionManagerWithConditionEvaluation extends PositionManager {
  ThreeState evaluateCondition(@NotNull EvaluationContext context,
                               @NotNull StackFrameProxyImpl frame,
                               @NotNull Location location,
                               @NotNull String expression);
}
