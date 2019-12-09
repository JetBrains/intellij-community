// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.messages.FileDeletedEvent;
import org.jetbrains.jps.incremental.messages.FileGeneratedEvent;

import java.util.EventListener;

/**
 * Implement this listener and {@link CompileContext#addBuildListener register} the implementation to receive events about files created,
 * modified or deleted by the build process.
 */
public interface BuildListener extends EventListener {
  /**
   * This method is called when a builder creates new files or modifies existing files. <br>
   * Note: when parallel build is on, might be called from several simultaneously running threads.
   */
  default void filesGenerated(@NotNull FileGeneratedEvent event) {
  }

  default void filesDeleted(@NotNull FileDeletedEvent event) {
  }
}
