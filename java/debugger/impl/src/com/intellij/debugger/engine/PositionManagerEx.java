/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.debugger.engine;

import com.intellij.debugger.PositionManager;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.util.ThreeState;
import com.intellij.xdebugger.frame.XStackFrame;
import com.sun.jdi.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class PositionManagerEx implements PositionManager {
  @Nullable
  public abstract XStackFrame createStackFrame(@NotNull StackFrameProxyImpl frame, @NotNull DebugProcessImpl debugProcess, @NotNull Location location);

  public abstract ThreeState evaluateCondition(@NotNull EvaluationContext context,
                                               @NotNull StackFrameProxyImpl frame,
                                               @NotNull Location location,
                                               @NotNull String expression);
}
