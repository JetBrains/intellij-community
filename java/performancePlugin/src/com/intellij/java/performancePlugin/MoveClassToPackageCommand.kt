// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.performancePlugin

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.PackageWrapper
import com.intellij.refactoring.move.moveClassesOrPackages.AutocreatingSingleSourceRootMoveDestination
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesProcessor
import com.jetbrains.performancePlugin.PerformanceTestSpan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Move the class under the caret to the given package in the same source root.
 * Syntax: %moveClassToPackage packageName
 */
@Suppress("DuplicatedCode")
class MoveClassToPackageCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {

  companion object {
    const val PREFIX: String = "${CMD_PREFIX}moveClassToPackage"
    const val SPAN_NAME: String = "moveClassToPackage"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val project = context.project
    val targetPackageName: String = extractCommandArgument(PREFIX)

    val editor = readAction { FileEditorManager.getInstance(context.project).selectedTextEditor }
                 ?: throw IllegalArgumentException("There is no selected editor")

    val elementUnderCaret = readAction {
      PsiDocumentManager.getInstance(project).getPsiFile(editor.document)?.findElementAt(editor.caretModel.offset)
    } ?: throw IllegalStateException("Unable to locate the element under the caret")

    val psiClass = PsiTreeUtil.getParentOfType(elementUnderCaret, PsiClass::class.java)
                   ?: throw IllegalStateException("Caret is not inside a class")

    val targetPackage = PackageWrapper(PsiManager.getInstance(project), targetPackageName)
    readAction { if (!targetPackage.exists()) throw IllegalArgumentException("Package $targetPackageName does not exist") }

    val sourceRoot = readAction { ProjectRootManager.getInstance(project).fileIndex.getSourceRootForFile(editor.virtualFile!!) }
                     ?: throw IllegalArgumentException("No source root found for ${editor.virtualFile!!.path}")

    val destination = AutocreatingSingleSourceRootMoveDestination(targetPackage, sourceRoot)

    val span = PerformanceTestSpan.TRACER.spanBuilder(SPAN_NAME).startSpan()

    val processor: BaseRefactoringProcessor = runReadAction {
      MoveClassesOrPackagesProcessor(project, arrayOf(psiClass), destination, false, false, null)
    }

    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        processor.run()
        span.end()
      }
    }
  }
}
