// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.autoimport.changes.vfs

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.openapi.vfs.newvfs.NewVirtualFile
import com.intellij.openapi.vfs.newvfs.events.*
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal class VirtualFileChangesSeparator(listener: VirtualFileChangesListener, events: List<VFileEvent>) {

  private val beforeAppliers = ArrayList<() -> Unit>()
  private val afterAppliers = ArrayList<() -> Unit>()

  private fun before(action: () -> Unit) = beforeAppliers.add(action)

  private fun after(action: () -> Unit) = afterAppliers.add(action)

  fun processBeforeEvents() = beforeAppliers.forEach { it() }

  fun processAfterEvents() = afterAppliers.forEach { it() }

  private fun VirtualFileChangesListener.process(f: VirtualFile, event: VFileEvent) {
    when (isProcessRecursively()) {
      true -> processRecursively(f, event)
      else -> processFile(f, event)
    }
  }

  private fun VirtualFileChangesListener.processFile(f: VirtualFile, event: VFileEvent) {
    if (isRelevant(f, event)) {
      updateFile(f, event)
    }
  }

  private fun VirtualFileChangesListener.processRecursively(f: VirtualFile, event: VFileEvent) {
    VfsUtilCore.visitChildrenRecursively(f, object : VirtualFileVisitor<Void>() {
      override fun visitFile(f: VirtualFile): Boolean {
        processFile(f, event)
        return true
      }

      override fun getChildrenIterable(f: VirtualFile): Iterable<VirtualFile>? {
        return if (f.isDirectory && f is NewVirtualFile) f.iterInDbChildren() else null
      }
    })
  }

  init {
    for (each in events) {
      ProgressManager.checkCanceled()

      when (each) {
        is VFilePropertyChangeEvent -> if (each.isRename) {
          before {
            listener.process(each.file, each)
          }
          after {
            listener.process(each.file, each)
          }
        }
        is VFileMoveEvent -> {
          before {
            listener.process(each.file, each)
          }
          after {
            listener.process(each.file, each)
          }
        }
        is VFileCopyEvent -> after {
          val newFile = each.newParent.findChild(each.newChildName)
          if (newFile != null) {
            listener.process(newFile, each)
          }
        }
        is VFileCreateEvent -> after {
          val file = each.file
          if (file != null) {
            listener.process(file, each)
          }
        }
        is VFileDeleteEvent, is VFileContentChangeEvent -> before {
          val file = each.file
          if (file != null) {
            listener.process(file, each)
          }
        }
      }
    }
  }
}