// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.codeInsight.completion.command.*
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.java.JavaBundle
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.Nls
import javax.swing.Icon

internal class JavaDeleteCompletionCommandProvider : CommandProvider {
  override fun getCommands(context: CommandCompletionProviderContext): List<CompletionCommand> {
    val element = getCommandContext(context.offset, context.psiFile) ?: return emptyList()
    var psiElement = PsiTreeUtil.getParentOfType(element, PsiStatement::class.java, PsiMember::class.java) ?: return emptyList()
    val hasTheSameOffset = psiElement.textRange.endOffset == context.offset
    if (!hasTheSameOffset) return emptyList()
    psiElement = getTopWithTheSameOffset(psiElement, context.offset)
    val highlightInfo = HighlightInfoLookup(psiElement.textRange, EditorColors.SEARCH_RESULT_ATTRIBUTES, 0)
    return listOf(createCommand(highlightInfo, psiElement))
  }

  private fun createCommand(highlightInfo: HighlightInfoLookup, psiElement: PsiElement): CompletionCommand {
    val preview = if (psiElement is PsiClass) {
      IntentionPreviewInfo.CustomDiff(JavaFileType.INSTANCE, null, "", psiElement.text, false)
    }
    else {
      val copy = psiElement.containingFile.copy() as PsiFile
      val previewBefore = copy.text
      val elementToDelete = PsiTreeUtil.findSameElementInCopy(psiElement, copy)
      elementToDelete.delete()
      val previewAfter = copy.text
      IntentionPreviewInfo.CustomDiff(
        JavaFileType.INSTANCE,
        null,
        previewBefore,
        previewAfter,
        true
      )
    }
    return JavaDeleteCompletionCommand(highlightInfo, preview)
  }
}

private fun getTopWithTheSameOffset(psiElement: PsiElement, offset: Int): PsiElement {
  var psiElement1 = psiElement
  var curElement = psiElement1
  while (curElement.textRange.endOffset == offset) {
    psiElement1 = curElement
    curElement = PsiTreeUtil.getParentOfType(curElement, PsiStatement::class.java, PsiMember::class.java) ?: break
  }
  return psiElement1
}

private class JavaDeleteCompletionCommand(
  override val highlightInfo: HighlightInfoLookup?,
  private val preview: IntentionPreviewInfo,
) : CompletionCommand(), CompletionCommandWithPreview, DumbAware {
  override val name: String
    get() = "Delete element"
  override val i18nName: @Nls String
    get() = JavaBundle.message("command.completion.delete.element.text")
  override val icon: Icon?
    get() = null

  override fun execute(offset: Int, psiFile: PsiFile, editor: Editor?) {
    val element = getCommandContext(offset, psiFile) ?: return
    var psiElement = PsiTreeUtil.getParentOfType(element, PsiStatement::class.java, PsiMember::class.java) ?: return
    psiElement = getTopWithTheSameOffset(psiElement, offset)
    WriteCommandAction.runWriteCommandAction(psiFile.project, null, null, {
      val parent: SmartPsiElementPointer<PsiElement?> = SmartPointerManager.createPointer(psiElement.parent ?: psiFile)
      psiElement.delete()
      PsiDocumentManager.getInstance(psiFile.project).commitDocument(psiFile.fileDocument)
      parent.element?.let {
        ReformatCodeProcessor(psiFile, arrayOf(it.textRange)).run()
      }
    }, psiFile)
  }

  override fun getPreview(): IntentionPreviewInfo? {
    return preview
  }
}