// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.tools.combined

import com.intellij.diff.editor.DiffViewerVirtualFile
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
abstract class CombinedDiffVirtualFile(name: String, private val path: String = name) : DiffViewerVirtualFile(name) {
  override fun getPath(): String = path
  abstract override fun createViewer(project: Project): CombinedDiffComponentProcessor
}

@ApiStatus.Internal
@ApiStatus.Experimental
class CombinedDiffVirtualFileImpl(val project: Project, val producers: List<CombinedBlockProducer>, name: String, path: String = name)
  : CombinedDiffVirtualFile(name, path) {
  override fun createViewer(project: Project): CombinedDiffComponentProcessor {
    val processor = CombinedDiffManager.getInstance(project).createProcessor()
    processor.setBlocks(producers)
    return processor
  }
}
