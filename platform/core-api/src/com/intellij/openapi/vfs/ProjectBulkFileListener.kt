// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import org.jetbrains.annotations.ApiStatus

/**
 * A listener for VFS events specific to the project, invoked **on a background thread**.
 *
 * To register this listener, use [ProjectBulkFileListenerRegistry.addListener]
 * or define an extension in `plugin.xml`:
 * ```xml
 * <vfs.projectBulkListener implementation="com.example.ProjectBulkFileListenerImpl"/>
 * ```
 *
 * Differences from [com.intellij.openapi.vfs.newvfs.BulkFileListener]:
 *
 * 1. The listener always runs on a background thread.
 * 2. The listener receives events only for files that belong to the project.
 * 3. The listener receives events for children of the affected files
 *
 * Events for children:
 *
 * Some events affect entire file trees. For example, moving a directory, also moves all its children.
 * However, [com.intellij.openapi.vfs.newvfs.BulkFileListener] and [AsyncFileListener] will receive event only for the directory itself and not for its children.
 * On the other hand, this listener will receive events for all affected files (in case of move, for all children of moved directory).
 *
 * @see AsyncFileListener
 */
@ApiStatus.Experimental
interface ProjectBulkFileListener {
  /**
   * Runs on a background thread under the write lock after the VFS change.
   */
  @RequiresWriteLock
  fun afterVfsChange(events: List<VFileEvent>, project: Project) {
  }
}
