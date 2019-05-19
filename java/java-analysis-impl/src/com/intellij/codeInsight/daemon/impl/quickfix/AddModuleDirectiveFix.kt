// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.util.PsiUtil

abstract class AddModuleDirectiveFix(module: PsiJavaModule) : LocalQuickFixAndIntentionActionOnPsiElement(module) {
  override fun getFamilyName(): String = QuickFixBundle.message("module.info.add.directive.family.name")

  override fun isAvailable(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement): Boolean =
    startElement is PsiJavaModule && PsiUtil.isLanguageLevel9OrHigher(file) && BaseIntentionAction.canModify(startElement)

  override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement): Unit =
    invoke(project, file, editor, startElement as PsiJavaModule)

  protected abstract fun invoke(project: Project, file: PsiFile, editor: Editor?, module: PsiJavaModule)
}

class AddRequiresDirectiveFix(module: PsiJavaModule, private val requiredName: String) : AddModuleDirectiveFix(module) {
  override fun getText(): String = QuickFixBundle.message("module.info.add.requires.name", requiredName)

  override fun invoke(project: Project, file: PsiFile, editor: Editor?, module: PsiJavaModule) {
    if (module.requires.find { requiredName == it.moduleName } == null) {
      PsiUtil.addModuleStatement(module, PsiKeyword.REQUIRES + ' ' + requiredName)
    }
  }
}

class AddExportsDirectiveFix(module: PsiJavaModule,
                             private val packageName: String,
                             private val targetName: String) : AddModuleDirectiveFix(module) {
  override fun getText(): String = QuickFixBundle.message("module.info.add.exports.name", packageName)

  override fun invoke(project: Project, file: PsiFile, editor: Editor?, module: PsiJavaModule) {
    val existing = module.exports.find { packageName == it.packageName }
    if (existing == null) {
      PsiUtil.addModuleStatement(module, PsiKeyword.EXPORTS + ' ' + packageName)
    }
    else if (!targetName.isEmpty()) {
      val targets = existing.moduleNames
      if (!targets.isEmpty() && targetName !in targets) {
        existing.add(PsiElementFactory.getInstance(project).createModuleReferenceFromText(targetName, null))
      }
    }
  }
}

class AddUsesDirectiveFix(module: PsiJavaModule, private val svcName: String) : AddModuleDirectiveFix(module) {
  override fun getText(): String = QuickFixBundle.message("module.info.add.uses.name", svcName)

  override fun invoke(project: Project, file: PsiFile, editor: Editor?, module: PsiJavaModule) {
    if (module.uses.find { svcName == it.classReference?.qualifiedName } == null) {
      PsiUtil.addModuleStatement(module, PsiKeyword.USES + ' ' + svcName)
    }
  }
}