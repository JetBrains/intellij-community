// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs

import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import org.jetbrains.annotations.ApiStatus

/**
 * A listener for VFS events, invoked inside write-action.
 *
 * The thread of execution is **implementation-defined**. It can be EDT or a background thread.
 * It is guaranteed that [before] and [after] will be invoked on the same thread, but there is no guarantee that the instance of this listener
 * will be used only on one thread.
 *
 * Please use [com.intellij.openapi.vfs.AsyncFileListener] instead, unless you absolutely sure you need to receive events synchronously.
 *
 * To register this listener, use e.g. `project.getMessageBus().connect(disposable).subscribe(VirtualFileManager.VFS_CHANGES_BG, listener)`
 * or define the listener in `plugin.xml` as an application listener (the preferred way):
 * ```
 * <applicationListeners>
 *   <listener class="com.plugin.MyBulkFileListener"
 *             topic="com.intellij.openapi.vfs.newvfs.BulkFileListenerBackgroundable"/>
 * </applicationListeners>
 * ```
 * Please note that the VFS events are project-agnostic so all listeners will be notified about events from all open projects.
 * For filtering the events use [com.intellij.openapi.roots.ProjectRootManager.getFileIndex] with
 * [com.intellij.openapi.roots.FileIndex.isInContent]
 */
@ApiStatus.Experimental
interface BulkFileListenerBackgroundable {

  @RequiresWriteLock
  // can be invoked on any thread
  fun before(events: List<VFileEvent>) {}

  @RequiresWriteLock
  // can be invoked on any thread
  fun after(events: List<VFileEvent>) {}
}