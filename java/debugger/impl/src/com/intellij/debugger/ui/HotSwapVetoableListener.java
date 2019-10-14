// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.debugger.ui;

import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.DummyCompileContext;
import com.intellij.task.ProjectTaskContext;
import com.intellij.task.impl.JpsBuildData;
import com.intellij.task.impl.JpsProjectTaskRunner;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Allows plugins to cancel hotswap after a particular compilation session.
 * @see HotSwapUI#addListener(HotSwapVetoableListener)
 */
public interface HotSwapVetoableListener {

  /**
   * Returns {@code false} if Hot Swap shouldn't be invoked after the given compilation session.
   *
   * @deprecated use {@link #shouldHotSwap(ProjectTaskContext)}
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.3")
  @Deprecated
  default boolean shouldHotSwap(CompileContext finishedCompilationContext) { return true; }

  /**
   * Returns {@code false} if Hot Swap shouldn't be invoked after the given compilation session.
   */
  default boolean shouldHotSwap(@NotNull ProjectTaskContext context) {
    CompileContext compileContext = DummyCompileContext.getInstance();
    JpsBuildData jpsBuildData = context.getUserData(JpsProjectTaskRunner.JPS_BUILD_DATA_KEY);
    if (jpsBuildData != null && jpsBuildData.getFinishedBuildsContexts().size() == 1) {
      compileContext = jpsBuildData.getFinishedBuildsContexts().get(0);
    }
    return shouldHotSwap(compileContext);
  }
}
