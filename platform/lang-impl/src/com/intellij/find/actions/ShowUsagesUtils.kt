// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.actions

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.editor.CaretModel
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.navigation.NavigationOptions
import com.intellij.platform.ide.navigation.NavigationService
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.usages.Usage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls

fun EditorToPsiMethod(project: Project, editor: Editor): PsiMethod {
  // Get current caret offset
  val caretModel: CaretModel = editor.caretModel
  val caretOffset: Int = caretModel.offset

  // Get the document corresponding to the editor
  val document: Document = editor.document

  // Get the PsiFile corresponding to the document
  val psiFile: PsiFile? = PsiDocumentManager.getInstance(project).getPsiFile(document)

  // Get the PSI element at the caret offset
  val elementAtCaret: PsiElement? = psiFile?.findElementAt(caretOffset)

  // Ascend the tree to find the enclosing PsiMethod
  return PsiTreeUtil.getParentOfType(elementAtCaret, PsiMethod::class.java)!!
}

internal fun navigateAndHint(project: Project,
                             usage: Usage,
                             hint: @Nls(capitalization = Nls.Capitalization.Sentence) String,
                             parameters: ShowUsagesParameters,
                             actionHandler: ShowUsagesActionHandler,
                             onReady: Runnable) {
  println("Inline_code_usage_go")
  // Code below need EDT
  val curEditor = parameters.editor
  (project as ComponentManagerEx).getCoroutineScope().launch(Dispatchers.EDT) {
    NavigationService.getInstance(project).navigate(usage, NavigationOptions.defaultOptions().requestFocus(true))
    writeIntentReadAction {
      val newEditor = getEditorFor(usage)
      if (newEditor == null) {
        onReady.run()
        return@writeIntentReadAction
      }

      // Ascend the tree to find the enclosing PsiMethod
      val psiMethodTo = EditorToPsiMethod(project, newEditor)
      val psiMethodFrom = EditorToPsiMethod(project, curEditor!!)

      addEdgeToJourney(project, psiMethodFrom, psiMethodTo)
    }
  }
}

fun addEdgeToJourney(
  project: Project,
  psiMethodFrom: PsiElement,
  psiMethodTo: PsiElement,
) {
  val addEdge = project.getUserData(Project.JOURNEY_ADD_EDGE)
  addEdge!!.accept(psiMethodFrom, psiMethodTo)
}

internal fun getEditorFor(usage: Usage): Editor? {
  val location = usage.location
  val newFileEditor = location?.editor
  return if (newFileEditor is TextEditor) newFileEditor.editor else null
}