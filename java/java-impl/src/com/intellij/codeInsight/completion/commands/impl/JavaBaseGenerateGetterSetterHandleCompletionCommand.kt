// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.codeInsight.completion.command.CommandCompletionProviderContext
import com.intellij.codeInsight.completion.command.CommandProvider
import com.intellij.codeInsight.completion.command.CompletionCommand
import com.intellij.codeInsight.completion.command.CompletionCommandWithPreview
import com.intellij.codeInsight.completion.command.HighlightInfoLookup
import com.intellij.codeInsight.completion.command.getCommandContext
import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.intention.QuickFixFactory
import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler
import com.intellij.codeInsight.intention.impl.preview.IntentionPreviewComputable
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.modcommand.ActionContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.psi.PsiField
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.util.PsiTreeUtil
import javax.swing.Icon

internal class GenerateGetterSetterHandleCompletionCommandProvider : CommandProvider {
  override fun getCommands(context: CommandCompletionProviderContext): List<CompletionCommand> {
    val element = getCommandContext(context.offset, context.psiFile) ?: return emptyList()
    if (element !is PsiIdentifier) return emptyList()
    val field = PsiTreeUtil.getParentOfType(element, PsiField::class.java) ?: return emptyList()

    val result = mutableListOf<CompletionCommand>()
    val possibleCases = listOf(
      GetterSetterCase(true, true, "Generate 'Getter/Setter'", QuickFixBundle.message("create.getter.setter")),
      GetterSetterCase(false, true, "Generate 'Setter'", QuickFixBundle.message("create.setter")),
      GetterSetterCase(true, false, "Generate 'Getter'", QuickFixBundle.message("create.getter")),
    )
    for (case in possibleCases) {
      val action = QuickFixFactory.getInstance()
        .createCreateGetterOrSetterFix(case.generateGetter, case.generateSetter, field)
      val actionContext = ActionContext.from(context.editor, context.psiFile).withElement(field)
      val modCommandAction = action.asModCommandAction()
      if (modCommandAction == null || modCommandAction.getPresentation(actionContext) == null) continue
      result.add(BaseGenerateGetterSetterHandleCompletionCommand(case.generateGetter,
                                                                 case.generateSetter,
                                                                 case.name,
                                                                 case.i18nName,
                                                                 HighlightInfoLookup(field.textRange, EditorColors.SEARCH_RESULT_ATTRIBUTES, 0)) {
        IntentionPreviewComputable(context.project, action, context.psiFile, context.editor, context.offset).call()
      })
    }
    return result
  }
}

private data class GetterSetterCase(val generateGetter: Boolean, val generateSetter: Boolean, val name: String, val i18nName: String)

private class BaseGenerateGetterSetterHandleCompletionCommand(
  val generateGetter: Boolean,
  val generateSetter: Boolean,
  override val name: String,
  override val i18nName: String,
  override val highlightInfo: HighlightInfoLookup?,
  private val preview: () -> IntentionPreviewInfo?,
) : CompletionCommand(), CompletionCommandWithPreview {

  override val icon: Icon? = null

  override fun execute(offset: Int, psiFile: PsiFile, editor: Editor?) {
    val element = getCommandContext(offset, psiFile) ?: return
    val field = PsiTreeUtil.getParentOfType(element, PsiField::class.java) ?: return
    val action = QuickFixFactory.getInstance().createCreateGetterOrSetterFix(generateGetter, generateSetter, field)
    if (editor == null) return
    @Suppress("DialogTitleCapitalization")
    ShowIntentionActionsHandler.chooseActionAndInvoke(psiFile, editor, action, action.text)
  }

  override fun getPreview(): IntentionPreviewInfo? {
    return preview()
  }
}