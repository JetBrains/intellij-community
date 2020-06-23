// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A listener for VFS events, invoked inside write-action.
 * To register this listener, use e.g. {@code project.getMessageBus().connect(disposable).subscribe(VirtualFileManager.VFS_CHANGES, listener)}
 * or define the listener in {@code plugin.xml} as an application listener (the preferred way):
 * <pre>
 *     	&#60;applicationListeners>
 *     	  &#60;listener class="com.plugin.MyBulkFileListener"
 *                  topic="com.intellij.openapi.vfs.newvfs.BulkFileListener"/>
 *   	&#60;/applicationListeners>
 *</pre>
 * 
 * Please note that the VFS events are project-agnostic so all listeners will be notified about events from all open projects. 
 * For filtering the events use {@link com.intellij.openapi.roots.ProjectRootManager#getFileIndex} with {@link com.intellij.openapi.roots.FileIndex#isInContent}.
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