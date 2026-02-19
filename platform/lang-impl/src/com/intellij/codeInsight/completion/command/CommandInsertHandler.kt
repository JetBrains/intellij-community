// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.injected.editor.DocumentWindow
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil
import org.jetbrains.annotations.ApiStatus

/**
 * A handler for managing the insertion of commands during code completion.
 * This class manages specific behaviors that occur when a command from the lookup
 * list is selected and inserted into the editor.
 */
@ApiStatus.Internal
internal class CommandInsertHandler(private val completionCommand: CompletionCommand) : InsertHandler<LookupElement?> {
  override fun handleInsert(context: InsertionContext, item: LookupElement) {
    var editor = context.editor
    val originalEditor = editor.getUserData(ORIGINAL_EDITOR)
    var startOffset: Int = -1
    var psiFile = context.file
    val commandProcessor = CommandProcessor.getInstance()
    if (completionCommand.customPrefixMatcher("") == null) {
      if (originalEditor != null) {
        startOffset = originalEditor.second
        editor = originalEditor.first
        psiFile = PsiDocumentManager.getInstance(context.project).getPsiFile(editor.getDocument()) ?: return
        val installedEditor = editor.getUserData(INSTALLED_EDITOR) ?: return
        Disposer.dispose(installedEditor)
      }
      else {
        commandProcessor.executeCommand(context.project, {
          // Remove the dots and command text from the document
          startOffset = removeCommandText(context)
        }, commandProcessor.currentCommandName, commandProcessor.currentCommandGroupId)
      }
    }
    else {
      startOffset = context.tailOffset
    }

    if (startOffset == -1) return

    ApplicationManager.getApplication().invokeLater {
      commandProcessor.runUndoTransparentAction(
        {
          completionCommand.execute(startOffset, psiFile, editor)
        }
      )
    }
  }

  private fun removeCommandText(context: InsertionContext): Int {
    val topLevelEditor = InjectedLanguageEditorUtil.getTopLevelEditor(context.editor)
    val injectedLanguageManager = InjectedLanguageManager.getInstance(context.project)
    val document: Document = topLevelEditor.document
    val tailOffset = injectedLanguageManager.injectedToHost(context.file, context.tailOffset)
    val startOffset = injectedLanguageManager.injectedToHost(context.file, context.startOffset)
    val service = context.project.service<CommandCompletionService>()
    val commandCompletionFactory = service.getFactory(context.file.language) ?: return startOffset
    val completionType = findCommandCompletionType(commandCompletionFactory, !context.file.isWritable, tailOffset, topLevelEditor)
    if (completionType != null) {
      CommandCompletionCollector.called(completionCommand::class.java,
                                        context.file.language,
                                        completionType::class.java)
    }

    val actualIndex = findActualIndex(commandCompletionFactory.suffix().toString() + (commandCompletionFactory.filterSuffix() ?: ""),
                                      document.immutableCharSequence,
                                      startOffset)

    // Remove the command text after the dots
    var commandStart = 0.coerceAtLeast(startOffset - actualIndex)

    // Delete from commandStart to tailOffset
    document.deleteString(commandStart, tailOffset)

    // Adjust the caret position
    topLevelEditor.caretModel.moveToOffset(commandStart)
    context.commitDocument()
    if (topLevelEditor != context.editor) {
      commandStart = (context.editor.document as? DocumentWindow)?.hostToInjected(commandStart) ?: commandStart
    }
    return commandStart
  }
}