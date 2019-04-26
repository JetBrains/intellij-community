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

import com.intellij.compiler.impl.CompileContextImpl;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.DummyCompileContext;
import com.intellij.task.ProjectTaskContext;
import com.intellij.task.ProjectTaskResult;
import org.jetbrains.annotations.NotNull;

/**
 * Allows plugins to cancel hotswap after a particular compilation session.
 * @see HotSwapUI#addListener(HotSwapVetoableListener)
 */
public interface HotSwapVetoableListener {

  /**
   * Returns {@code false} if Hot Swap shouldn't be invoked after the given compilation session.
   * @deprecated use {@link #shouldHotSwap(ProjectTaskContext, ProjectTaskResult)}
   */
  @Deprecated
  default boolean shouldHotSwap(CompileContext finishedCompilationContext) { return true; }

  /**
   * Returns {@code false} if Hot Swap shouldn't be invoked after the given compilation session.
   */
  default boolean shouldHotSwap(@NotNull ProjectTaskContext context, @NotNull ProjectTaskResult finishedTasksResult) {
    CompileContext compileContext = context.getUserData(CompileContextImpl.CONTEXT_KEY);
    return shouldHotSwap(compileContext != null ? compileContext : DummyCompileContext.getInstance());
  }
}
