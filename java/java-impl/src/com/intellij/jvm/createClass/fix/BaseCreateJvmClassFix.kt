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
package com.intellij.jvm.createClass.fix

import com.intellij.codeInsight.daemon.QuickFixBundle.message
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.jvm.createClass.CreateClassRequest
import com.intellij.jvm.createClass.CreateJvmClassFactory
import com.intellij.jvm.createClass.SourceClassKind
import com.intellij.jvm.createClass.ui.CreateClassDialog
import com.intellij.jvm.createClass.ui.CreateClassUserInfo
import com.intellij.lang.Language
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import com.intellij.openapi.module.ModuleUtilCore.findModuleForPsiElement
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference

abstract class BaseCreateJvmClassFix<R : PsiReference> : IntentionAction {

  protected lateinit var myClassName: String

  override fun getFamilyName(): String = message("create.class.from.usage.family")

  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
    val reference = reference ?: return false
    myClassName = getClassName(reference) ?: return false
    return true
  }

  override fun startInWriteAction(): Boolean = false

  abstract val reference: R?

  abstract fun getClassName(reference: R): String?

  abstract fun getFactories(reference: R): Map<SourceClassKind, CreateJvmClassFactory>

  abstract fun createRequest(reference: R, userInfo: CreateClassUserInfo): CreateClassRequest

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    file ?: return
    val reference = reference ?: return
    val module = findModuleForPsiElement(file) ?: return

    val factoryMap = getFactories(reference)
    val kindMap: Map<Language, List<SourceClassKind>> = factoryMap.keys.groupBy { it.language }
    val dialog = CreateClassDialog(project, module).apply {
      initKinds(kindMap)
      initClassName(false, myClassName)
      initTargetPackage(null)
      init()
    }
    if (!dialog.showAndGet()) return
    val userInfo = dialog.userInfo ?: return

    val classKind = userInfo.classKind
    val factory = factoryMap[classKind] ?: error("Wrong class kind: $classKind")
    val request = createRequest(reference, userInfo)

    val clazz = runWriteAction {
      IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace()
      factory.createClass(request).apply {
        reference.bindToElement(this)
      }
    }
    clazz.navigate(true)
  }
}