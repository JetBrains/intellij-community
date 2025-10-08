// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.breadcrumbs

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.breadcrumbs.Crumb
import com.intellij.ui.components.breadcrumbs.StickyLineInfo

private object DefaultFileBreadcrumbsCollector : FileBreadcrumbsCollector() {
  override fun handlesFile(virtualFile: VirtualFile): Boolean = true

  /**
   * Some parts of the platform expect nullable FileBreadcrumbsCollector, `true` here makes `DefaultFileBreadcrumbsCollector` skippable.
   */
  override fun requiresProvider(): Boolean = true

  override fun watchForChanges(file: VirtualFile, editor: Editor, disposable: Disposable, changesHandler: Runnable) {
  }

  override fun computeCrumbs(virtualFile: VirtualFile, document: Document, offset: Int, forcedShown: Boolean?): Iterable<Crumb?> {
    return emptyList()
  }

  override fun computeStickyLineInfos(file: VirtualFile, document: Document, offset: Int): List<StickyLineInfo?> {
    return emptyList()
  }
}