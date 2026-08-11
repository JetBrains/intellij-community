// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl.hotswap

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.xdebugger.impl.hotswap.HotSwapChangesCompatibility
import com.intellij.xdebugger.impl.hotswap.SourceFileChange
import kotlinx.coroutines.runBlocking

object HotSwapSourceChangeCompatibilityCheckerTestUtil {
  fun classifyDocumentChange(
    project: Project,
    checker: JvmBaseSourceFileChangeCompatibilityChecker,
    currentFile: PsiFile,
    before: String,
    after: String,
    validateOriginal: Boolean = true,
  ): HotSwapChangesCompatibility {
    if (validateOriginal) {
      val originalCompatibility = checker.getCompatibility(currentFile, before)
      if (originalCompatibility !== HotSwapChangesCompatibility.Compatible) {
        throw AssertionError("original state expected:<${HotSwapChangesCompatibility.Compatible}> but was:<$originalCompatibility>")
      }
    }

    replaceFileText(project, currentFile.virtualFile, after)

    return checker.getCompatibility(currentFile, before)
  }

  @Suppress("RAW_RUN_BLOCKING")
  private fun JvmBaseSourceFileChangeCompatibilityChecker.getCompatibility(
    currentFile: PsiFile,
    oldContent: CharSequence,
  ): HotSwapChangesCompatibility = runBlocking {
    getCompatibility(SourceFileChange(currentFile.virtualFile, oldContent))
  }

  private fun replaceFileText(project: Project, file: VirtualFile, text: String) {
    WriteCommandAction.runWriteCommandAction(project) {
      val document = FileDocumentManager.getInstance().getDocument(file)!!
      document.setText(text)
      PsiDocumentManager.getInstance(project).commitDocument(document)
    }
  }
}
