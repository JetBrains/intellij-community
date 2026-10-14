// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.universal

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileChooser.universal.NioPathCompletion.State
import com.intellij.openapi.fileChooser.universal.NioPathCompletion.Trigger
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.update.DebouncedUpdates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.jetbrains.annotations.ApiStatus
import java.awt.Point
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.nio.file.Path
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import kotlin.time.Duration.Companion.milliseconds

/**
 * A path text field with a completion popup for the children of the typed directory.
 * The [NioPathCompletion] finds the suggestions, and the [NioPathCompletionPopup] shows them.
 *
 * @param showHidden returns true when the completion lists the hidden children. Runs on the EDT.
 * @param pathParser converts an absolute path text into a path, see [UniversalFileChooserContributor.parsePresentablePath].
 *   The completion uses only an absolute result, and expands `~` with [homeDirectory] before the parse.
 *   The parser also decides the path syntax, for example the path separators.
 *   It runs on the EDT and in a background coroutine, so it must be fast and must not touch the file system.
 * @param prepareCompletion runs in a background coroutine before the children are listed.
 *   Lets a caller open a remote connection off the EDT.
 * @param homeDirectory returns the absolute home directory text for the `~` expansion, or null when the home is unknown.
 *   Runs in a background coroutine after [prepareCompletion]. The completion calls it once and keeps the result.
 *   A failure of [prepareCompletion] or [homeDirectory] shows in the completion popup.
 */
@ApiStatus.Internal
class NioPathTextField(
  scope: CoroutineScope,
  chooseFiles: Boolean,
  chooseArchives: Boolean = false,
  showHidden: () -> Boolean = { false },
  pathParser: (String) -> Path? = { text -> runCatching { Path.of(text) }.getOrNull()?.takeIf { it.isAbsolute } },
  prepareCompletion: suspend () -> Unit = {},
  homeDirectory: suspend () -> String? = { null },
) : JBTextField() {

  private val popup = NioPathCompletionPopup(owner = this, location = ::popupLocation, onChoose = ::chooseSelected)

  private val completion = NioPathCompletion(
    scope = scope,
    chooseFiles = chooseFiles,
    chooseArchives = chooseArchives,
    showHidden = showHidden,
    pathParser = pathParser,
    prepareCompletion = prepareCompletion,
    homeDirectory = homeDirectory,
    onStateChanged = ::showState,
  )

  // Set while the completion edits the document itself, so the edit does not re-trigger the completion.
  private var documentUpdating = false

  // The text at the last caret event. A caret event with the same text is a caret move without an edit.
  private var caretText: String = text

  // Merges the edits, so the typing updates the completion at most once per [EDIT_DELAY].
  // The update runs in a later EDT event, so the caret and the text view are current.
  private val editQueue = DebouncedUpdates.forScope<Unit>(scope, "NioPathTextField.edits", EDIT_DELAY)
    .withContext(Dispatchers.EDT)
    .withComponentModality(this)
    .runLatest { updateCompletion(Trigger.TYPING) }

  /** True while the completion popup is visible. */
  val isCompletionPopupVisible: Boolean
    get() = popup.isVisible

  init {
    document.addDocumentListener(object : DocumentListener {
      override fun insertUpdate(e: DocumentEvent) = queueEditUpdate()
      override fun removeUpdate(e: DocumentEvent) = queueEditUpdate()
      override fun changedUpdate(e: DocumentEvent) {}
    })

    addCaretListener { caretMoved() }

    // The popup never takes the focus, so the field forwards the list keys while the popup is visible.
    addKeyListener(object : KeyAdapter() {
      override fun keyPressed(e: KeyEvent) = processListSelection(e)
    })

    addFocusListener(object : FocusAdapter() {
      override fun focusLost(e: FocusEvent) = cancelCompletion()
    })

    object : DumbAwareAction() {
      override fun actionPerformed(e: AnActionEvent) = updateCompletion(Trigger.SHORTCUT)
    }.registerCustomShortcutSet(KeymapUtil.getActiveKeymapShortcuts(IdeActions.ACTION_CODE_COMPLETION), this)
  }

  /**
   * Sets the text without the completion, and cancels the running completion.
   * The owner sets the text, for example from the tree selection, so the popup does not open for it.
   */
  override fun setText(text: String?) {
    cancelCompletion()
    updateDocument { super.setText(text) }
  }

  suspend fun expandHome(text: String): String = completion.expandHome(text)

  override fun removeNotify() {
    cancelCompletion()
    super.removeNotify()
  }

  /** Updates the completion for the current text. The update covers all pending edits. */
  private fun updateCompletion(trigger: Trigger) {
    editQueue.cancelPending()
    if (isFocusOwner) completion.update(completionBase(), trigger)
  }

  /** Queues the completion update for an edit. The edits of the completion itself stay muted. */
  private fun queueEditUpdate() {
    if (!documentUpdating) editQueue.queue(Unit)
  }

  /** Closes the completion when the caret moves without an edit. */
  private fun caretMoved() {
    val edited = text != caretText
    caretText = text
    if (!edited && !documentUpdating) cancelCompletion()
  }

  /**
   * Returns the text before the caret. The completion works on this text only.
   * The text after the caret does not change the suggestions.
   */
  private fun completionBase(): String = text.substring(0, caretPosition)

  private fun showState(state: State) {
    if (!isShowing) {
      popup.close()
      return
    }
    when (state) {
      State.Hidden -> popup.close()
      is State.Loading -> popup.showLoading(state.progressText)
      State.NoResults -> popup.showNoResults()
      is State.Suggestions -> popup.showSuggestions(state.matching)
      is State.Failed -> popup.showFailed()
    }
  }

  /**
   * Returns the screen point just below the start of the typed name, after the last separator.
   * The popup does not move while the user types the name. Falls back to the bottom left corner of the field.
   */
  private fun popupLocation(): Point {
    val anchorOffset = completion.directoryTextOf(completionBase())?.length ?: caretPosition
    val anchorBounds = runCatching { modelToView2D(anchorOffset) }.getOrNull()
    val point = if (anchorBounds == null) Point(0, height) else Point(anchorBounds.maxX.toInt(), anchorBounds.maxY.toInt())
    // The anchor can be outside the field when the text scrolls horizontally.
    point.x = point.x.coerceIn(0, width)
    SwingUtilities.convertPointToScreen(point, this)
    point.y += JBUI.scale(POPUP_CARET_GAP)
    return point
  }

  private fun processListSelection(e: KeyEvent) {
    if (!popup.isVisible) return
    // Enter acts on the current text, so the edits that still wait in the queue apply first.
    if (e.keyCode == KeyEvent.VK_ENTER) updateCompletion(Trigger.TYPING)
    if (!popup.isVisible) return
    if (!completion.hasSuggestionsFor(completionBase())) {
      // The popup has no list, or the list belongs to the previous directory, so only Escape applies.
      if (e.keyCode == KeyEvent.VK_ESCAPE) {
        dismissCompletion()
        e.consume()
      }
      return
    }
    when (e.keyCode) {
      KeyEvent.VK_ENTER -> {
        // With no selection, the key keeps its default action, for example the dialog OK action.
        if (popup.selectedValue != null && e.modifiersEx == 0) {
          chooseSelected()
          e.consume()
        }
      }
      KeyEvent.VK_ESCAPE -> {
        dismissCompletion()
        e.consume()
      }
      else -> if (popup.moveSelection(e)) e.consume()
    }
  }

  /**
   * Applies the completion edit for the selected child, and puts the caret at the end.
   * A selected directory continues the completion inside it.
   */
  private fun chooseSelected() {
    val chosen = popup.selectedValue ?: return
    val edit = completion.complete(completionBase(), chosen) ?: return
    updateDocument {
      document.remove(edit.offset, document.length - edit.offset)
      document.insertString(edit.offset, edit.text, null)
      caretPosition = document.length
    }
    if (edit.descends) updateCompletion(Trigger.TYPING)
  }

  /** Runs the [change] of the document with the completion muted. */
  private inline fun updateDocument(change: () -> Unit) {
    documentUpdating = true
    try {
      change()
    }
    finally {
      documentUpdating = false
    }
  }

  /** Closes the popup and stops the popup on the next edits until the completion shortcut. */
  private fun dismissCompletion() {
    editQueue.cancelPending()
    completion.dismiss()
  }

  /** Cancels the pending completion and closes the popup. The listing stays. */
  private fun cancelCompletion() {
    editQueue.cancelPending()
    completion.cancel()
  }

  private companion object {
    val EDIT_DELAY = 100.milliseconds
    const val POPUP_CARET_GAP = 2
  }
}
