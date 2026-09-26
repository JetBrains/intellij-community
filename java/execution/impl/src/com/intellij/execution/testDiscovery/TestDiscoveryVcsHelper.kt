// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testDiscovery

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface TestDiscoveryVcsHelper {
  fun getAffectedFiles(project: Project, changeListName: String): List<VirtualFile>

  fun getChangedTextRanges(project: Project, file: PsiFile): List<TextRange>

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<TestDiscoveryVcsHelper> =
      ExtensionPointName.create("com.intellij.execution.testDiscoveryVcsHelper")

    @JvmStatic
    fun collectAffectedFiles(project: Project, changeListName: String): List<VirtualFile> =
      EP_NAME.extensionList.firstOrNull()?.getAffectedFiles(project, changeListName) ?: emptyList()

    @JvmStatic
    fun collectChangedTextRanges(project: Project, file: PsiFile): List<TextRange> =
      EP_NAME.extensionList.firstOrNull()?.getChangedTextRanges(project, file) ?: emptyList()
  }
}
