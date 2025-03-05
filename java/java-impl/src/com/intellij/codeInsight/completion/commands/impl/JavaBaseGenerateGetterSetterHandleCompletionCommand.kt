// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.codeInsight.completion.command.ApplicableCompletionCommand
import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.intention.QuickFixFactory
import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler
import com.intellij.modcommand.ActionContext
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiField
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.util.PsiTreeUtil
import javax.swing.Icon


internal class GenerateGetterSetterHandleCompletionCommand :
  BaseGenerateGetterSetterHandleCompletionCommand(true, true, "Generate 'Getter/Setter'",
                                                  QuickFixBundle.message("create.getter.setter"))

internal class GenerateSetterHandleCompletionCommand :
  BaseGenerateGetterSetterHandleCompletionCommand(false, true, "Generate 'Setter'",
                                                  QuickFixBundle.message("create.setter"))

internal class GenerateGetterHandleCompletionCommand :
  BaseGenerateGetterSetterHandleCompletionCommand(true, false, "Generate 'Getter'",
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
    if (element !is PsiIdentifier) return false
    val field = PsiTreeUtil.getParentOfType(element, PsiField::class.java) ?: return false
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