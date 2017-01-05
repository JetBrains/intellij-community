/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.module.impl

import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.impl.storage.ClasspathStorage
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent

/**
 * Why this class is required if we have StorageVirtualFileTracker?
 * Because StorageVirtualFileTracker doesn't detect (intentionally) parent file changes —
 *
 * If module file is foo/bar/hello.iml and directory foo is renamed to oof then we must update module path. And StorageVirtualFileTracker doesn't help us here (and is not going to help by intention).
 */
internal class ModuleFileListener(private val moduleManager: ModuleManagerComponent) : BulkFileListener.Adapter() {
  override fun after(events: List<VFileEvent>) {
    for (event in events) {
      when (event) {
        is VFilePropertyChangeEvent -> propertyChanged(event)
        is VFileMoveEvent -> fileMoved(event)
      }
    }
  }

  private fun propertyChanged(event: VFilePropertyChangeEvent) {
    if (event.requestor is StateStorage || VirtualFile.PROP_NAME != event.propertyName) {
      return
    }

    val parentPath = event.file.parent?.path ?: return
    for (module in moduleManager.modules) {
      if (!module.isLoaded) {
        continue
      }

      val ancestorPath = "$parentPath/${event.oldValue}"
      val moduleFilePath = module.moduleFilePath
      if (FileUtil.isAncestor(ancestorPath, moduleFilePath, true)) {
        setModuleFilePath(module, "$parentPath/${event.newValue}/${FileUtil.getRelativePath(ancestorPath, moduleFilePath, '/')}")
      }
    }
  }

  private fun fileMoved(event: VFileMoveEvent) {
    if (!event.file.isDirectory) {
      return
    }

    val dirName = event.file.nameSequence
    val ancestorPath = "${event.oldParent.path}/$dirName"
    for (module in moduleManager.modules) {
      if (!module.isLoaded) {
        continue
      }

      val moduleFilePath = module.moduleFilePath
      if (FileUtil.isAncestor(ancestorPath, moduleFilePath, true)) {
        setModuleFilePath(module, "${event.newParent.path}/$dirName/${FileUtil.getRelativePath(ancestorPath, moduleFilePath, '/')}")
      }
    }
  }

  private fun setModuleFilePath(module: Module, newFilePath: String) {
    ClasspathStorage.modulePathChanged(module, newFilePath)
    module.stateStore.setPath(FileUtilRt.toSystemIndependentName(newFilePath))
  }
}