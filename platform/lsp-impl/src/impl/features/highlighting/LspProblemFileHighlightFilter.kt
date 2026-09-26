// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.impl.features.highlighting

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Condition
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.impl.LspClientManagerImpl

/**
 * Allows [com.intellij.problems.WolfTheProblemSolver] to accept error reports for files
 * that have an active LSP server open. Without this filter, Wolf silently ignores
 * [com.intellij.problems.WolfTheProblemSolver.reportProblemsFromExternalSource] calls.
 */
internal class LspProblemFileHighlightFilter(private val project: Project) : Condition<VirtualFile> {
  override fun value(file: VirtualFile): Boolean {
    return LspClientManagerImpl.getInstanceImpl(project).getClientsWithThisFileOpen(file).isNotEmpty()
  }
}
