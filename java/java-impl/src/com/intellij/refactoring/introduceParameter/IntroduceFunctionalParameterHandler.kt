/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.refactoring.introduceParameter

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.HelpID
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.extractMethod.ExtractMethodHandler
import com.intellij.refactoring.util.CommonRefactoringUtil

object IntroduceFunctionalParameterHandler : IntroduceParameterHandler() {
  private val REFACTORING_NAME = RefactoringBundle.message("introduce.functional.parameter.title")

  private fun showErrorMessage(project: Project, editor: Editor) {
    val message = RefactoringBundle.getCannotRefactorMessage(
        RefactoringBundle.message("is.not.supported.in.the.current.context", REFACTORING_NAME)
    )
    CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.INTRODUCE_PARAMETER)
  }

  private fun doInvoke(editor: Editor, elements: Array<PsiElement>, file: PsiFile, project: Project) {
    if (!introduceStrategy(project, editor, file, elements)) showErrorMessage(project, editor)
  }

  override fun invoke(project: Project, elements: Array<PsiElement>, dataContext: DataContext?) {
    if (dataContext == null) return
    val file = CommonDataKeys.PSI_FILE.getData(dataContext) ?: return
    val editor = CommonDataKeys.EDITOR.getData(dataContext) ?: return
    doInvoke(editor, elements, file, project)
  }

  override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext) {
    ExtractMethodHandler.selectAndPass(
        project,
        editor,
        file,
        object : Pass<Array<PsiElement>>() {
          override fun pass(elements: Array<PsiElement>) = doInvoke(editor, elements, file, project)
        }
    )
  }
}