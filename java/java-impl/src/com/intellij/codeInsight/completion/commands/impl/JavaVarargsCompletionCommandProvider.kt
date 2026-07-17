// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.completion.command.CommandCompletionProviderContext
import com.intellij.codeInsight.completion.command.CommandProvider
import com.intellij.codeInsight.completion.command.CompletionCommand
import com.intellij.codeInsight.completion.commands.JavaCommandCompletionFactory
import com.intellij.java.JavaBundle
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.Nls

internal class JavaVarargsCompletionCommandProvider : CommandProvider, DumbAware {
  override fun getCommands(context: CommandCompletionProviderContext): List<CompletionCommand> {
    if (context.isReadOnly) return emptyList()
    if (!JavaCommandCompletionFactory.isAfterTypeElementDotsInParameterList(context.psiFile,
                                                                            context.offset, 2)) return emptyList()
    return listOf(JavaVarargsCompletionCommand())
  }
}

private class JavaVarargsCompletionCommand : CompletionCommand() {
  override val presentableName: @Nls String
    get() = JavaBundle.message("command.completion.varargs.dot.text")

  override val additionalInfo: String
    get() = JavaBundle.message("command.completion.varargs.info")

  override val priority: Int
    get() = 1000

  override fun customPrefixMatcher(prefix: String): PrefixMatcher {
    return ExactPrefixMatcher(prefix)
  }

  override fun execute(offset: Int, psiFile: PsiFile, editor: Editor?) {
    val document = editor?.document ?: PsiDocumentManager.getInstance(psiFile.project).getDocument(psiFile) ?: return
    if (document.charsSequence.length <= offset || offset < 3) {
      return
    }
    if (document.charsSequence.substring(offset - 3, offset) == "...") {
      return
    }
    if (document.charsSequence[offset] == '.') {
      return
    }
    WriteAction.run<RuntimeException> {
      var string = "..."
      if (document.charsSequence.substring(offset - 2, offset) == "..") {
        string = "."
      }
      if (offset < document.textLength && document.charsSequence[offset] != ' ') {
        string += " "
      }
      document.insertString(offset, string)
      PsiDocumentManager.getInstance(psiFile.project).commitDocument(document)
      editor?.caretModel?.moveToOffset(offset + string.length)
    }
  }

  private class ExactPrefixMatcher(prefix: String) : PrefixMatcher(prefix) {

    override fun prefixMatches(element: String): Boolean {
      return myPrefix == "" && element == "."
    }

    override fun cloneWithPrefix(prefix: String): PrefixMatcher {
      if (prefix == "") return this
      return ExactPrefixMatcher(prefix)
    }
  }
}