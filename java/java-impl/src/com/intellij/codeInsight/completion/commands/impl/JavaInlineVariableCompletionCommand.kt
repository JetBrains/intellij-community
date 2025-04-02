// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.codeInsight.completion.command.CommandCompletionProviderContext
import com.intellij.codeInsight.completion.command.CommandProvider
import com.intellij.codeInsight.completion.command.CompletionCommand
import com.intellij.codeInsight.completion.command.CompletionCommandWithPreview
import com.intellij.codeInsight.completion.command.HighlightInfoLookup
import com.intellij.codeInsight.completion.command.getCommandContext
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.icons.AllIcons
import com.intellij.idea.ActionsBundle
import com.intellij.java.JavaBundle
import com.intellij.lang.refactoring.InlineActionHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.Nls
import javax.swing.Icon

internal class JavaInlineVariableCompletionCommandProvider : CommandProvider {
  override fun getCommands(context: CommandCompletionProviderContext): List<CompletionCommand> {
    val element = getCommandContext(context.offset, context.psiFile) ?: return emptyList()
    if (element !is PsiIdentifier) return emptyList()

    val javaRef = PsiTreeUtil.getParentOfType(element, PsiJavaCodeReferenceElement::class.java) ?: return emptyList()
    val psiElement = javaRef.resolve() ?: return emptyList()

    // Check if any inline handler can handle this element
    val editor = context.editor
    val extensionList = InlineActionHandler.EP_NAME.extensionList
    for (extension in extensionList) {
      try {
        if (extension.canInlineElementInEditor(psiElement, editor)) {
          val highlightInfo = element.textRange?.let {
            HighlightInfoLookup(it, EditorColors.SEARCH_RESULT_ATTRIBUTES, 0)
          }
          return listOf(JavaInlineVariableCompletionCommand(highlightInfo))
        }
      }
      catch (_: Exception) {
        continue
      }
    }

    return emptyList()
  }
}

private class JavaInlineVariableCompletionCommand(
  override val highlightInfo: HighlightInfoLookup?,
) : CompletionCommand(), DumbAware, CompletionCommandWithPreview {
  override val name: String
    get() = "Inline"

  override val i18nName: @Nls String
    get() = JavaBundle.message("command.completion.inline.text")


  override val icon: Icon?
    get() = AllIcons.Actions.RefactoringBulb // Use an appropriate icon from IntelliJ's icon set

  override fun execute(offset: Int, psiFile: PsiFile, editor: Editor?) {
    if (editor == null) return
    val element = getCommandContext(offset, psiFile) ?: return
    if (element !is PsiIdentifier) return

    val javaRef = PsiTreeUtil.getParentOfType(element, PsiJavaCodeReferenceElement::class.java) ?: return
    val psiElement = javaRef.resolve() ?: return

    val extensionList = InlineActionHandler.EP_NAME.extensionList
    for (extension in extensionList) {
      if (extension.canInlineElement(psiElement)) {
        extension.inlineElement(psiFile.project, editor, psiElement)
        return
      }
    }
  }

  override fun getPreview(): IntentionPreviewInfo? {
    return IntentionPreviewInfo.Html(ActionsBundle.message("action.Inline.description"))
  }
}