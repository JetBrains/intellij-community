// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.performancePlugin

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.inline.InlineMethodProcessor
import com.jetbrains.performancePlugin.PerformanceTestSpan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Inline the Java method under the caret.
 * Syntax: %inlineJavaMethod
 */
@Suppress("DuplicatedCode")
class InlineJavaMethodCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {

  companion object {
    const val PREFIX: String = "${CMD_PREFIX}inlineJavaMethod"
    const val SPAN_NAME: String = "inlineJavaMethod"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val project = context.project


    val editor = readAction { FileEditorManager.getInstance(context.project).selectedTextEditor }
                 ?: throw IllegalArgumentException("There is no selected editor")


    val elementUnderCaret = readAction {
      PsiDocumentManager.getInstance(project).getPsiFile(editor.document)?.findElementAt(editor.caretModel.offset)
    } ?: throw IllegalStateException("Unable to locate the element under the caret")

    val psiMethod = PsiTreeUtil.getParentOfType(elementUnderCaret, PsiMethod::class.java)
                    ?: throw IllegalStateException("Caret is not inside a method")

    val span = PerformanceTestSpan.TRACER.spanBuilder(SPAN_NAME).startSpan()

    val processor: BaseRefactoringProcessor = runReadAction {
      InlineMethodProcessor(project, psiMethod, null, editor, false, false, false, true)
    }

    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        processor.run()
        span.end()
      }
    }
  }
}
