// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.editor

import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.impl.DiffEditorViewer
import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.openapi.ListSelection
import com.intellij.openapi.project.Project

@Deprecated("Use DiffViewerVirtualFile instead", replaceWith = ReplaceWith("DiffViewerVirtualFile"))
abstract class DiffVirtualFile(name: String) : DiffViewerVirtualFile(name) {

  @Deprecated("Use createViewer instead", replaceWith = ReplaceWith("createViewer"))
  abstract fun createProcessor(project: Project): DiffRequestProcessor

  override fun createViewer(project: Project): DiffEditorViewer = createProcessor(project)
}

abstract class DiffViewerVirtualFile(name: String) : DiffVirtualFileBase(name) {

  abstract fun createViewer(project: Project): DiffEditorViewer
}

interface DiffVirtualFileWithProducers {
  fun collectDiffProducers(selectedOnly: Boolean): ListSelection<out DiffRequestProducer>?
}
