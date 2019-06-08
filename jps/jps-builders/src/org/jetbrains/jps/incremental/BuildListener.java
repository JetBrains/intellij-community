// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.messages.FileDeletedEvent;
import org.jetbrains.jps.incremental.messages.FileGeneratedEvent;

import java.util.EventListener;

/**
 * @author Eugene Zhuravlev
 */
public interface BuildListener extends EventListener{
  /**
   * Note: when parallel build is on, might be called from several simultaneously running threads
   * @param event
   */
  default void filesGenerated(@NotNull FileGeneratedEvent event) {
  }

  default void filesDeleted(@NotNull FileDeletedEvent event) {
  }
}
