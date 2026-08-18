// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.listener.listener
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.rememberAppliedValue
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.Document
import com.intellij.ui.SearchTextField as IdeaSearchTextField

/**
 * A single-line search field: a text field carrying the search icon, a clear button and a drop-down of
 * the queries it has held before.
 *
 * The caller owns [text]. It is written onto the field on every pass that declares a value the field is
 * not already holding, and [onTextChange] reports the user's own edits only, never the write that
 * applies [text]. An edit the caller does not adopt is left standing in the field rather than written
 * back, so the user is never fought mid-typing.
 *
 * The field adds its current text to its history when it loses focus and offers that history from a
 * popup under the search icon. [historyPropertyName] is the key that history is loaded from and stored
 * under in the application-level [com.intellij.ide.util.PropertiesComponent]: persistent IDE state,
 * shared with every other field using the same key, and reachable only where an application is running.
 * A field without one keeps its history in memory, for as long as the component lives. The key is fixed
 * when the field is created; declaring a different one replaces the field.
 *
 * @see com.intellij.ui.SearchTextField
 */
@Composable
@ApiStatus.Experimental
public fun SearchTextField(
  text: String,
  onTextChange: (String) -> Unit,
  modifier: SwingModifier = SwingModifier,
  historyPropertyName: @NonNls String? = null,
) {
  val currentOnTextChange = rememberUpdatedState(onTextChange)
  // The field's own content, mirrored: writing [text] onto the field raises the same document events an
  // edit does, and the mirror is what tells the two apart.
  val applied = rememberAppliedValue(text)
  val listener = remember(applied) {
    documentChangeListener { document ->
      val edited = document.getText(0, document.length)
      if (applied.observed(edited)) currentOnTextChange.value(edited)
    }
  }
  key(historyPropertyName) {
    SwingNode(
      factory = { IdeaSearchTextField(true, historyPropertyName) },
      update = {
        set(text) { declared ->
          applied.settle(declared, { this.text }, { this.text = it })
        }
        applyModifier(modifier.searchDocumentListener(listener))
      },
    )
  }
}

/**
 * Attaches [listener] to the document of the search field's text editor. The field is a panel around that
 * editor rather than a text component itself, so it carries the add/remove pair the listener seam needs.
 */
private fun SwingModifier.searchDocumentListener(listener: DocumentListener): SwingModifier =
  listener<IdeaSearchTextField, DocumentListener>(
    listener,
    { field, documentListener -> field.addDocumentListener(documentListener) },
    { field, documentListener -> field.removeDocumentListener(documentListener) },
  )

/** A [DocumentListener] that hands the changed document to [onChange] for an insert, a remove or an attribute change. */
private fun documentChangeListener(onChange: (Document) -> Unit): DocumentListener =
  object : DocumentListener {
    override fun insertUpdate(e: DocumentEvent): Unit = onChange(e.document)
    override fun removeUpdate(e: DocumentEvent): Unit = onChange(e.document)
    override fun changedUpdate(e: DocumentEvent): Unit = onChange(e.document)
  }
