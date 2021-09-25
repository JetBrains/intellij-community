// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.debugger.ui;

import com.intellij.task.ProjectTaskContext;
import org.jetbrains.annotations.NotNull;

/**
 * Allows plugins to cancel hotswap after a particular compilation session.
 * @see HotSwapUI#addListener(HotSwapVetoableListener)
 */
public interface HotSwapVetoableListener {
  /**
   * Returns {@code false} if Hot Swap shouldn't be invoked after the given compilation session.
   */
  boolean shouldHotSwap(@NotNull ProjectTaskContext context);
}
