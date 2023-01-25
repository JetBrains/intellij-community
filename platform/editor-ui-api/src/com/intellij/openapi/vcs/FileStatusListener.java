// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

public interface FileStatusListener {
  @Topic.ProjectLevel
  Topic<FileStatusListener> TOPIC = new Topic<>(FileStatusListener.class, Topic.BroadcastDirection.NONE, true);

  /**
   * Indicates that some file statuses were changed.
   * The client should recalculate all statuses it's dependent on.
   * <p>
   * The callback is called with {@link ModalityState#any()}, the clients should be careful about model modifications.
   */
  @RequiresEdt
  default void fileStatusesChanged() {
  }

  /**
   * The callback is called with {@link ModalityState#any()}, the clients should be careful about model modifications.
   */
  @RequiresEdt
  default void fileStatusChanged(@NotNull VirtualFile virtualFile) {
  }
}
