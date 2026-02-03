// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.build;

import com.intellij.build.events.BuildEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Build progress listener of build process.
 * Can be used to filter, convert and transfer build events between different build progress and output models.
 */
public interface BuildProgressListener {
  /**
   * This method is called when build {@code event} happens in listening build progress model with {@code buildId}.
   */
  void onEvent(@NotNull Object buildId, @NotNull BuildEvent event);
}
