// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport

import com.intellij.openapi.externalSystem.autoimport.changes.vfs.VirtualFileChangesListener
import com.intellij.openapi.externalSystem.autoimport.changes.vfs.VirtualFileChangesSeparator
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.*
import org.jetbrains.annotations.ApiStatus

@Deprecated("Use VirtualFileChangesListener instead")
@ApiStatus.ScheduledForRemoval
abstract class AsyncFileChangeListenerBase : AsyncFileListener, VirtualFileChangesListener {

  protected open val processRecursively: Boolean = true

  protected open fun isRelevant(path: String): Boolean = false

  override fun isProcessRecursively(): Boolean = processRecursively

  override fun isRelevant(file: VirtualFile, event: VFileEvent): Boolean = isRelevant(file.path)

  override fun prepareChange(events: List<VFileEvent>): AsyncFileListener.ChangeApplier {
    val separator = VirtualFileChangesSeparator(this, events)
    return object : AsyncFileListener.ChangeApplier {
      override fun beforeVfsChange() {
        init()
        separator.processBeforeEvents()
      }

      override fun afterVfsChange() {
        separator.processAfterEvents()
        apply()
      }
    }
  }
}
