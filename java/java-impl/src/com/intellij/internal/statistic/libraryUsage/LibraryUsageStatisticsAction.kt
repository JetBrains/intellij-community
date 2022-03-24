// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.libraryUsage

import com.intellij.codeInsight.hint.HintManager
import com.intellij.internal.statistic.libraryJar.findJarVersion
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.Messages

class LibraryUsageStatisticsAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val editor = e.getRequiredData(CommonDataKeys.EDITOR)
    val caretOffset = e.getRequiredData(CommonDataKeys.CARET).offset
    val psiFile = e.getRequiredData(CommonDataKeys.PSI_FILE)
    val fileType = psiFile.fileType
    fun showErrorHint(message: String): Unit = HintManager.getInstance().showErrorHint(editor, message)

    val processor = LibraryUsageImportProcessor.EP_NAME.findFirstSafe { it.isApplicable(fileType) }
                    ?: return showErrorHint("LibraryUsageImportProcessor is not found for ${fileType.name} file type")

    val libraryDescriptorFinder = service<LibraryDescriptorFinderService>().cachedLibraryDescriptorFinder()
                                  ?: return showErrorHint("LibraryDescriptorFinder is not cached")

    val import = processor.imports(psiFile).find { caretOffset in it.textRange } ?: return showErrorHint("import at caret is not found")
    val qualifier = processor.importQualifier(import) ?: return showErrorHint("qualifier is null")
    val libraryName = libraryDescriptorFinder.findSuitableLibrary(qualifier) ?: return showErrorHint("suitable library in not found")
    val resolvedElement = processor.resolve(import) ?: return showErrorHint("failed to resolve")
    val libraryVersion = resolvedElement.let(::findJarVersion) ?: return showErrorHint("failed to find version")
    val libraryUsage = LibraryUsage(
      name = libraryName,
      version = libraryVersion,
      fileType = fileType,
    )

    Messages.showInfoMessage(project, libraryUsage.toString(), "Library Info")
  }
}