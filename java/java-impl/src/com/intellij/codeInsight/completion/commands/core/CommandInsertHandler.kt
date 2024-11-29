// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.core

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.completion.commands.api.CompletionCommand
import com.intellij.codeInsight.completion.commands.api.OldCompletionCommand
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
internal class CommandInsertHandler(private val completionCommand: CompletionCommand) : InsertHandler<LookupElement?> {
  override fun handleInsert(context: InsertionContext, item: LookupElement) {
    // Remove the dots and command text from the document
    val startOffset = removeCommandText(context)

    // Execute the command
    ApplicationManager.getApplication().invokeLater {
      CommandProcessor.getInstance().executeCommand(context.project, {
        if (completionCommand is OldCompletionCommand) {
          completionCommand.execute(startOffset, context.file, context.editor)
        }
        else {
          completionCommand.execute(startOffset, context.file)
        }
      }, completionCommand.name, completionCommand)
    }
  }

  private fun removeCommandText(context: InsertionContext): Int {
    val document: Document = context.document
    val tailOffset = context.tailOffset
    val startOffset = context.startOffset
    val service = context.project.service<CommandCompletionService>()
    val commandCompletionFactory = service.getFactory(context.file.language) ?: return startOffset

    val actualIndex = findActualIndex(commandCompletionFactory.suffix().toString() + (commandCompletionFactory.filterSuffix() ?: ""),
                                      document.immutableCharSequence,
                                      startOffset)
    // Remove the command text after the dots
    val commandStart = startOffset - actualIndex

    // Delete from commandStart to tailOffset
    document.deleteString(commandStart, tailOffset)

    // Adjust the caret position
    context.editor.caretModel.moveToOffset(commandStart)
    context.commitDocument()
    return commandStart
  }
}