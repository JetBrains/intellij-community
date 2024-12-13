// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.codeInsight.completion.commands.api.ApplicableCompletionCommand
import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.daemon.impl.UnusedSymbolUtil
import com.intellij.codeInsight.daemon.impl.analysis.LocalRefUseInfo
import com.intellij.codeInsight.intention.QuickFixFactory
import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler
import com.intellij.modcommand.ActionContext
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiField
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiModifier
import com.intellij.psi.util.PsiTreeUtil
import javax.swing.Icon


class GenerateGetterSetterHandleCompletionCommand :
  BaseGenerateGetterSetterHandleCompletionCommand(true, true, "Create Getter/Setter",
                                                  QuickFixBundle.message("create.getter.setter"))

class GenerateSetterHandleCompletionCommand :
  BaseGenerateGetterSetterHandleCompletionCommand(false, true, "Create Setter",
                                                  QuickFixBundle.message("create.setter"))

class GenerateGetterHandleCompletionCommand :
  BaseGenerateGetterSetterHandleCompletionCommand(true, false, "Create Getter",
                                                  QuickFixBundle.message("create.getter"))

abstract class BaseGenerateGetterSetterHandleCompletionCommand(
  val generateGetter: Boolean,
  val generateSetter: Boolean,
  override val name: String,
  override val i18nName: String,
) : ApplicableCompletionCommand() {

  override val icon: Icon? = null

  override fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean {
    val element = getContext(offset, psiFile) ?: return false
    val field = PsiTreeUtil.getParentOfType(element, PsiField::class.java) ?: return false
    val info = LocalRefUseInfo.forFile(psiFile)
    if ((!info.isReferencedForRead(field) && generateGetter && !generateSetter ||
         !info.isReferencedForWrite(field) && generateSetter && !generateGetter ||
         generateSetter && generateGetter && !info.isReferenced(field)) &&
        !UnusedSymbolUtil.isImplicitUsage(psiFile.project, field) &&
        field.hasModifierProperty(PsiModifier.PRIVATE)) {
      return false
    }
    val action = QuickFixFactory.getInstance().createCreateGetterOrSetterFix(generateGetter, generateSetter, field)
    val context = ActionContext.from(editor, psiFile).withElement(field)
    return action.asModCommandAction()?.getPresentation(context) != null
  }

  override fun execute(offset: Int, psiFile: PsiFile, editor: Editor?) {
    val element = getContext(offset, psiFile) ?: return
    val field = PsiTreeUtil.getParentOfType(element, PsiField::class.java) ?: return
    val action = QuickFixFactory.getInstance().createCreateGetterOrSetterFix(generateGetter, generateSetter, field)
    if (editor == null) return
    @Suppress("DialogTitleCapitalization")
    ShowIntentionActionsHandler.chooseActionAndInvoke(psiFile, editor, action, action.text)
  }
}