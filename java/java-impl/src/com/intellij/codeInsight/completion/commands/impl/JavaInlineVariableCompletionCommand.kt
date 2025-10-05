// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.codeInsight.completion.command.*
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.idea.ActionsBundle
import com.intellij.java.JavaBundle
import com.intellij.lang.refactoring.InlineActionHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.Nls

internal class JavaInlineVariableCompletionCommandProvider : CommandProvider {
  override fun getCommands(context: CommandCompletionProviderContext): List<CompletionCommand> {
    val element = getCommandContext(context.offset, context.psiFile) ?: return emptyList()
    if (element !is PsiIdentifier) return emptyList()

    val javaRef = PsiTreeUtil.getParentOfType(element, PsiJavaCodeReferenceElement::class.java) ?: return emptyList()
    val psiElement = javaRef.resolve() ?: return emptyList()
    if (psiElement !is PsiVariable) return emptyList()
    if (psiElement is PsiField && psiElement.initializer == null) return emptyList()
    if (psiElement is PsiParameter) return emptyList()
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
) : CompletionCommand(), DumbAware {

  override val additionalInfo: String?
    get() {
      val shortcutText = KeymapUtil.getFirstKeyboardShortcutText("Inline")
      if (shortcutText.isNotEmpty()) {
        return shortcutText
      }
      return null
    }

  override val synonyms: List<String>
    get() = listOf("inline", "insert")

  override val presentableName: @Nls String
    get() = JavaBundle.message("command.completion.inline.text")

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

  override fun getPreview(): IntentionPreviewInfo {
    return IntentionPreviewInfo.Html(ActionsBundle.message("action.Inline.description"))
  }
}