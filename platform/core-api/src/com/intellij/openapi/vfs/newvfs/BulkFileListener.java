// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A listener for VFS events, invoked inside write-action.
 * To register this listener, use e.g. {@code project.getMessageBus().connect(disposable).subscribe(VirtualFileManager.VFS_CHANGES, listener)}
 *
 * For a non-blocking alternative please see {@link com.intellij.openapi.vfs.AsyncFileListener}.
 */
public interface BulkFileListener {
  /** @deprecated obsolete, implement {@link BulkFileListener} directly */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
  class Adapter implements BulkFileListener { }

  default void before(@NotNull List<? extends VFileEvent> events) { }

  default void after(@NotNull List<? extends VFileEvent> events) { }
}