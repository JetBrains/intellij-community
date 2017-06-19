/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.jvm.createClass.impl

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.jvm.createClass.api.CreateClassInfo
import com.intellij.jvm.createClass.api.CreateClassRequest
import com.intellij.jvm.createClass.api.computeActions
import com.intellij.jvm.createClass.impl.ui.CreateClassDialog
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference

abstract class CreateJvmClassFix<T : PsiReference>(
  protected val myProject: Project,
  protected val referenceName: String
) : IntentionAction {

  override fun getText(): String = QuickFixBundle.message("create.class.from.usage.another.language")

  override fun getFamilyName(): String = QuickFixBundle.message("create.class.from.usage.family")

  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = reference != null

  override fun startInWriteAction(): Boolean = false

  abstract val reference: T?

  abstract fun createRequests(reference: T): List<CreateClassRequest>

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    file ?: return
    val reference = reference ?: return
    val module = ModuleUtilCore.findModuleForPsiElement(file)

    val allActions = computeActions(createRequests(reference))
    val languageActions = allActions.groupBy { it.classKind.language }
    val kindMap = languageActions.mapValues { it.value.map { it.classKind } }
    val actionsMap = languageActions.mapValues { it.value.map { it.classKind to it }.toMap() }

    val dialog = CreateClassDialog(project, module).apply {
      initKinds(kindMap)
      initClassName(false, referenceName)
      initTargetPackage(null)
      init()
    }
    if (!dialog.showAndGet()) return

    val classKind = dialog.selectedClassKind
    val action = actionsMap[dialog.selectedLanguage]!![classKind]!!
    val info = CreateClassInfo(reference.element, classKind, dialog.className, dialog.targetDirectory)

    val clazz = runWriteAction {
      action.createClass(info).apply {
        reference.bindToElement(this)
        IdeDocumentHistory.getInstance(myProject).includeCurrentPlaceAsChangePlace()
      }
    }
    clazz.navigate(true)
  }
}
