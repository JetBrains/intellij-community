// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion

import com.intellij.codeWithMe.isOnGuest
import com.intellij.injected.editor.EditorWindow
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.command.CommandEvent
import com.intellij.openapi.command.CommandListener
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.util.messages.MessageBusConnection

/**
 * Tracks changes and actions performed on an editor instance.
 *
 * @see hasAnythingHappened
 */
internal class ActionTracker(
  private val myEditor: Editor,
  parentDisposable: Disposable,
) {
  private val myProject: Project = myEditor.getProject() ?: ProjectManager.getInstance().getDefaultProject()
  private val myConnection: MessageBusConnection = myProject.getMessageBus().connect(parentDisposable)

  private var myCaretOffsets: List<Int>? = null
  private var myStartDocStamp: Long = 0
  private var myActionsHappened = false
  private val myIsDumb: Boolean = DumbService.getInstance(myProject).isDumb
  private val happenedActions = mutableListOf<String>()

  init {
    myConnection.subscribe(AnActionListener.TOPIC, object : AnActionListener {
      override fun beforeActionPerformed(action: AnAction, event: AnActionEvent) {
        myActionsHappened = true
        if (LOG.isTraceEnabled) {
          happenedActions.add("Action class = ${action.javaClass}, action = $action")
        }
      }
    })
    setDocumentState()
  }

  private fun setDocumentState() {
    myStartDocStamp = docStamp()
    myCaretOffsets = caretOffsets()
  }

  private fun caretOffsets(): List<Int> {
    return myEditor.caretModel.allCarets.map { it.offset }
  }

  private fun docStamp(): Long {
    return myEditor.uiDocument.modificationStamp
  }

  fun ignoreCurrentDocumentChange() {
    if (!CommandProcessor.getInstance().isCommandInProgress) {
      return
    }

    myConnection.subscribe(CommandListener.TOPIC, object : CommandListener {
      private var insideCommand: Boolean = true

      override fun commandFinished(event: CommandEvent) {
        if (insideCommand) {
          insideCommand = false
          setDocumentState()
        }
      }
    })
  }

  fun hasAnythingHappened(): Boolean {
    val hasDocumentOrCaretChanged = myStartDocStamp != docStamp() || myCaretOffsets != caretOffsets()
    return myActionsHappened ||
           myIsDumb != DumbService.getInstance(myProject).isDumb ||
           myEditor.isDisposed ||
           (myEditor is EditorWindow && !myEditor.isValid) ||
           (hasDocumentOrCaretChanged && !isOnGuest()) //do not track speculative changes on thin client
  }


  fun describeChangeEvent(): String = when {
    myActionsHappened -> {
      if (LOG.isTraceEnabled) {
        """The following actions were performed: $happenedActions"""
      }
      else {
        "Actions were performed"
      }
    }
    myIsDumb != DumbService.getInstance(myProject).isDumb -> {
      "DumbMode state changed"
    }
    myEditor.isDisposed -> {
      "Editor was disposed"
    }
    myEditor is EditorWindow && !myEditor.isValid -> {
      "Editor window became invalid"
    }
    myStartDocStamp != docStamp() -> {
      "Document was modified; myStartDocStamp =$myStartDocStamp, docStamp()=${docStamp()}"
    }
    myCaretOffsets != caretOffsets() -> {
      "Caret position changed; myCaretOffsets = $myCaretOffsets, caretOffsets()=${caretOffsets()}"
    }
    else -> {
      "No changes detected"
    }
  }
}

private val LOG = logger<ActionTracker>()
