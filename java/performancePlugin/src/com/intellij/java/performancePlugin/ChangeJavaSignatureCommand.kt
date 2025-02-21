// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.performancePlugin

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.changeSignature.ParameterInfoImpl
import com.intellij.refactoring.openapi.impl.JavaRefactoringFactoryImpl
import com.jetbrains.performancePlugin.PerformanceTestSpan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Change the signature of the Java method under the caret.
 * Syntax: %changeJavaSignature ACTION name
 * Example: %changeJavaSignature ADD_PARAMETER myParameter
 */
@Suppress("DuplicatedCode")
class ChangeJavaSignatureCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  private enum class ChangeSignatureAction { ADD_PARAMETER }

  companion object {
    const val PREFIX: String = "${CMD_PREFIX}changeJavaSignature"
    const val ADD_PARAM_SPAN_NAME: String = "changeJavaSignature: add parameter"

    private suspend fun handleAddParameter(project: Project, psiMethod: PsiMethod, name: String) {
      val refactoringFactory = JavaRefactoringFactoryImpl(project)
      @Suppress("DEPRECATION") val parameterInfo = arrayOf(ParameterInfoImpl(-1, name, PsiType.BOOLEAN, "false", true))

      val span = PerformanceTestSpan.TRACER.spanBuilder(ADD_PARAM_SPAN_NAME).startSpan()

      val processor = readAction {
        refactoringFactory.createChangeSignatureProcessor(
          psiMethod, false, "public", name, psiMethod.returnType, parameterInfo, null, null, null, null)
      }
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          processor.run()
          span.end()
        }
      }
    }
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val project = context.project
    val commandArgs = extractCommandArgument(PREFIX)

    val args = commandArgs.split(" ")
    val action = args.getOrNull(0)?.uppercase()?.let { mode -> ChangeSignatureAction.entries.find { it.name == mode } }
                 ?: throw IllegalArgumentException("Action is missing or wrong")
    val name = args.getOrNull(1) ?: throw IllegalArgumentException("Name is missing")
    if (args.size > 2) throw IllegalArgumentException("Too many arguments provided")


    val editor = readAction { FileEditorManager.getInstance(context.project).selectedTextEditor }
                 ?: throw IllegalArgumentException("There is no selected editor")


    val elementUnderCaret = readAction {
      PsiDocumentManager.getInstance(project).getPsiFile(editor.document)?.findElementAt(editor.caretModel.offset)
      ?: throw IllegalStateException("Unable to locate the element under the caret")
    }
    val psiMethod = PsiTreeUtil.getParentOfType(elementUnderCaret, PsiMethod::class.java)
                    ?: throw IllegalStateException("Caret is not inside a method")

    when (action) {
      ChangeSignatureAction.ADD_PARAMETER -> handleAddParameter(project, psiMethod, name)
    }
  }
}
