// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.content;

import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

public interface ContentManagerListener extends EventListener {
  default void contentAdded(@NotNull ContentManagerEvent event) {
  }

  /**
   * Called after content was removed from the manager.
   * <p>
   * But it can be removed temporarily, for example, to reorder the contents (like using drag and drop).
   * In this case, content has {@link Content#TEMPORARY_REMOVED_KEY} user data set to true.
   * <b>Avoid disposing the content resources</b> in the body of this method, or do that only
   * if the content is not removed temporarily.
   */
  default void contentRemoved(@NotNull ContentManagerEvent event) {
  }

  /**
   * Called before content is removed from the manager.
   * <p>
   * Can be used to perform some checks, for example, show a confirmation dialog.
   * And prohibit content removal if necessary by calling {@link ContentManagerEvent#consume()}.
   * <p>
   * This method isn't called when content is being removed temporarily ({@link Content#TEMPORARY_REMOVED_KEY} is set to true).
   */
  default void contentRemoveQuery(@NotNull ContentManagerEvent event) {
  }

  default void selectionChanged(@NotNull ContentManagerEvent event) {
  }
}