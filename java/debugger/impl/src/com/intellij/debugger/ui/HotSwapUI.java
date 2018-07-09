/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.debugger.ui;

import com.intellij.openapi.project.Project;
import com.intellij.debugger.impl.DebuggerSession;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public abstract class HotSwapUI {
  public static HotSwapUI getInstance(Project project) {
    return project.getComponent(HotSwapUI.class);
  }

  public abstract void reloadChangedClasses(@NotNull DebuggerSession session, boolean compileBeforeHotswap);

  public abstract void reloadChangedClasses(@NotNull DebuggerSession session, boolean compileBeforeHotswap,
                                            @Nullable HotSwapStatusListener callback);

  public abstract void dontPerformHotswapAfterThisCompilation();

  public abstract void addListener(HotSwapVetoableListener listener);

  public abstract void removeListener(HotSwapVetoableListener listener);
}
