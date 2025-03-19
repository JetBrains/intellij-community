// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.events

import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.psi.impl.PsiTreeChangeEventImpl
import com.intellij.psi.impl.PsiTreeChangePreprocessor
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexImpl
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class FileBasedIndexTreeChangePreprocessor : PsiTreeChangePreprocessor {
  private val vfsEventsMerger by lazy {
    val fileBasedIndex = FileBasedIndex.getInstance()
    if (fileBasedIndex !is FileBasedIndexImpl) return@lazy null
    fileBasedIndex.changedFilesCollector.eventMerger
  }

  override fun treeChanged(event: PsiTreeChangeEventImpl) {
    if (event.isGenericChange &&
        event.code == PsiTreeChangeEventImpl.PsiEventType.CHILDREN_CHANGED) {
      val file = event.file
      if (file != null) {
        val virtualFile = file.virtualFile
        if (virtualFile is VirtualFileWithId && !FileBasedIndexImpl.isMock(virtualFile)) {
          vfsEventsMerger?.recordTransientStateChangeEvent(virtualFile)
        }
      }
    }
  }
}