// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose

import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.semantics.SemanticsNode
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.command.undo.DocumentReference
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.command.undo.UndoableAction
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.util.Pair
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.bridge.ComposeSemanticsTreeUtils.findFocusedComponent
import org.jetbrains.jewel.bridge.ComposeSemanticsTreeUtils.getCustomAction
import org.jetbrains.jewel.foundation.InternalJewelApi
import java.awt.Component

/** This manager is used only for Compose editable text fields */
@ApiStatus.Internal
@OptIn(InternalJewelApi::class)
internal class ComposeUndoManager(val component: Component) : UndoManager() {
  val focusedComponent: SemanticsNode?
    get() {
      val parent = component.parent as? ComposePanel
      return parent?.findFocusedComponent()
    }

  override fun undo(editor: FileEditor?) {
    getUndoAction()?.action?.invoke()
  }

  override fun redo(editor: FileEditor?) {
    getRedoAction()?.action?.invoke()
  }

  override fun isUndoAvailable(editor: FileEditor?): Boolean = getUndoAction() != null

  override fun isRedoAvailable(editor: FileEditor?): Boolean = getRedoAction() != null

  private fun getUndoAction() = focusedComponent?.getCustomAction("Undo")

  private fun getRedoAction() = focusedComponent?.getCustomAction("Redo")

  override fun getUndoActionNameAndDescription(editor: FileEditor?): Pair<@NlsActions.ActionText String?, @NlsActions.ActionDescription String?> {
    return Pair(
      ActionsBundle.message("action.undo.text", "").trim(),
      ActionsBundle.message("action.undo.description", ActionsBundle.message("action.undo.description.empty")).trim()
    )
  }

  override fun getRedoActionNameAndDescription(editor: FileEditor?): Pair<@NlsActions.ActionText String?, @NlsActions.ActionDescription String?> {
    return Pair(
      ActionsBundle.message("action.redo.text", "").trim(),
      ActionsBundle.message("action.redo.description", ActionsBundle.message("action.redo.description.empty")).trim()
    )
  }

  override fun undoableActionPerformed(action: UndoableAction) {
  }

  override fun nonundoableActionPerformed(ref: DocumentReference, isGlobal: Boolean) {
  }

  override fun isUndoInProgress(): Boolean {
    return false
  }

  override fun isRedoInProgress(): Boolean {
    return false
  }

  override fun getNextUndoNanoTime(editor: FileEditor): Long {
    return -1L
  }

  override fun getNextRedoNanoTime(editor: FileEditor): Long {
    return -1L
  }

  override fun isNextUndoAskConfirmation(editor: FileEditor): Boolean {
    return false
  }

  override fun isNextRedoAskConfirmation(editor: FileEditor): Boolean {
    return false
  }
}