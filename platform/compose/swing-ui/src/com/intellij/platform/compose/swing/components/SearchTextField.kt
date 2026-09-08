// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.CallbackRegistration
import org.jetbrains.compose.swing.modifier.listener.ListenerRegistration
import org.jetbrains.compose.swing.modifier.listener.listener
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.rememberMirrorState
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
  // The field's own content, mirrored: writing [text] onto the field raises the same document events an
  // edit does, and the mirror is what tells the two apart.
  val mirror = rememberMirrorState(text)
  key(historyPropertyName) {
    SwingNode(
      factory = { IdeaSearchTextField(true, historyPropertyName) },
      modifier = modifier.listener(
        { document: Document ->
          val edited = document.getText(0, document.length)
          if (mirror.observed(edited)) onTextChange(edited)
        },
        SEARCH_FIELD_DOCUMENT,
      ),
      update = {
        set(text) { declared ->
          mirror.settle(declared, { this.text }, { this.text = it })
        }
      },
    )
  }
}

/**
 * The search field's own document add/remove pair. The field is a panel around its text editor rather than
 * a text component itself, so the library's `documentListener` does not reach it.
 */
private val SEARCH_FIELD_DOCUMENT_PAIR =
  ListenerRegistration<IdeaSearchTextField, DocumentListener>(
    name = "searchTextFieldDocument",
    attach = { field, documentListener -> field.addDocumentListener(documentListener) },
    detach = { field, documentListener -> field.removeDocumentListener(documentListener) },
  )

/**
 * [SEARCH_FIELD_DOCUMENT_PAIR] driven by a callback the library reads as the event fires, so a fresh lambda
 * on every pass registers nothing again. The callback is handed the changed document, for an insert, a
 * remove and an attribute change alike.
 */
private val SEARCH_FIELD_DOCUMENT =
  CallbackRegistration<IdeaSearchTextField, (Document) -> Unit, DocumentListener>(
    adapter = { current ->
      object : DocumentListener {
        override fun insertUpdate(e: DocumentEvent): Unit = current()(e.document)
        override fun removeUpdate(e: DocumentEvent): Unit = current()(e.document)
        override fun changedUpdate(e: DocumentEvent): Unit = current()(e.document)
      }
    },
    registration = SEARCH_FIELD_DOCUMENT_PAIR,
  )
