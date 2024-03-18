// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.editor

import com.intellij.diff.chains.DiffRequestChain
import com.intellij.diff.impl.CacheDiffRequestChainProcessor
import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project

open class ChainDiffVirtualFile(val chain: DiffRequestChain, name: String) : DiffVirtualFile(name), DiffVirtualFileWithTabName {
  override fun createProcessor(project: Project): DiffRequestProcessor = CacheDiffRequestChainProcessor(project, chain)

  override fun getEditorTabName(project: Project, editors: List<FileEditor>): String? {
    val processor = editors.filterIsInstance<DiffEditorViewerFileEditor>()
      .map { it.editorViewer }
      .filterIsInstance<DiffRequestProcessor>()
      .firstOrNull()
    return processor?.activeRequest?.title
  }

  override fun toString(): String = "${super.toString()}:$chain"
}