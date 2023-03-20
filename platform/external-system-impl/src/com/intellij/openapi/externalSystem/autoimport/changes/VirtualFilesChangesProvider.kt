// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport.changes

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemModificationType.EXTERNAL
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemModificationType.INTERNAL
import com.intellij.openapi.externalSystem.autoimport.changes.vfs.VirtualFileChangesListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.EventDispatcher

class VirtualFilesChangesProvider(private val isIgnoreInternalChanges: Boolean) : FilesChangesProvider, VirtualFileChangesListener {
  private val eventDispatcher = EventDispatcher.create(FilesChangesListener::class.java)

  override fun subscribe(listener: FilesChangesListener, parentDisposable: Disposable) {
    eventDispatcher.addListener(listener, parentDisposable)
  }

  override fun init() = eventDispatcher.multicaster.init()

  override fun apply() = eventDispatcher.multicaster.apply()

  override fun isRelevant(file: VirtualFile, event: VFileEvent) =
    !isIgnoreInternalChanges || !event.isFromSave

  override fun updateFile(file: VirtualFile, event: VFileEvent) {
    val modificationType = if (event.isFromRefresh) EXTERNAL else INTERNAL
    eventDispatcher.multicaster.onFileChange(file.path, file.modificationStamp, modificationType)
  }
}