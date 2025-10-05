// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.event;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.util.ArrayFactory;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

/**
 * Allows to receive notifications about changes in edited documents.
 * Implementations shouldn't modify the document, for which event is emitted, in listener methods.
 * <p>
 * Implement {@link BulkAwareDocumentListener.Simple} instead of this interface whenever possible to improve performance.
 *
 * @see Document#addDocumentListener(DocumentListener, Disposable)
 * @see EditorEventMulticaster#addDocumentListener(DocumentListener, Disposable)
 */
public interface DocumentListener extends EventListener {
  DocumentListener[] EMPTY_ARRAY = new DocumentListener[0];
  ArrayFactory<DocumentListener> ARRAY_FACTORY = count -> count == 0 ? EMPTY_ARRAY : new DocumentListener[count];

  /**
   * Called before the text of the document is changed.
   *
   * @param event the event containing the information about the change.
   */
  default void beforeDocumentChange(@NotNull DocumentEvent event) {
  }

  /**
   * Called after the text of the document has been changed.
   *
   * @param event the event containing the information about the change.
   */
  default void documentChanged(@NotNull DocumentEvent event) {
  }

  /**
   * Notifies about {@link Document#isInBulkUpdate() bulk mode} being started
   */
  default void bulkUpdateStarting(@NotNull Document document) {}

  /**
   * Notifies about {@link Document#isInBulkUpdate() bulk mode} being finished
   */
  default void bulkUpdateFinished(@NotNull Document document) {}
}
