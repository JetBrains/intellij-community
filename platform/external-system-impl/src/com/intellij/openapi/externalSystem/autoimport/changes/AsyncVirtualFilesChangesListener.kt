// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport.changes

import com.intellij.openapi.externalSystem.autoimport.ExternalSystemModificationType.EXTERNAL
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemModificationType.INTERNAL
import com.intellij.openapi.externalSystem.autoimport.changes.vfs.VirtualFileChangesListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class AsyncVirtualFilesChangesListener(
  private val isIgnoreInternalChanges: Boolean,
  private val listener: AsyncFileChangesListener,
) : VirtualFileChangesListener {

  override fun init() {
    listener.init()
  }

  override fun apply() {
    listener.apply()
  }

  override fun isRelevant(file: VirtualFile, event: VFileEvent): Boolean {
    return !isIgnoreInternalChanges || !event.isFromSave
  }

  override fun updateFile(file: VirtualFile, event: VFileEvent) {
    val modificationType = if (event.isFromRefresh) EXTERNAL else INTERNAL
    listener.onFileChange(file.path, file.modificationStamp, modificationType)
  }
}