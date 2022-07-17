// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.event;

import com.intellij.openapi.editor.Document;
import org.jetbrains.annotations.NotNull;

/**
 * Convenience interface for {@link DocumentListener}-s which only process notifications on document changes performed not in
 * {@link Document#isInBulkUpdate()}  bulk mode}.
 * <br>
 * If possible, this interface should be used in preference to {@link DocumentListener}, to improve performance.
 */
public interface BulkAwareDocumentListener extends DocumentListener {
  @Override
  default void beforeDocumentChange(@NotNull DocumentEvent event) {
    if (!event.getDocument().isInBulkUpdate()) beforeDocumentChangeNonBulk(event);
  }

  @Override
  default void documentChanged(@NotNull DocumentEvent event) {
    if (!event.getDocument().isInBulkUpdate()) documentChangedNonBulk(event);
  }

  default void beforeDocumentChangeNonBulk(@NotNull DocumentEvent event) {}
  default void documentChangedNonBulk(@NotNull DocumentEvent event) {}

  /**
   * Simple specialization of {@link BulkAwareDocumentListener} for the case when the listener doesn't need the details of the changes
   * (offsets and changed text), and is fine with receiving only one notification for changes done in bulk mode.
   */
  interface Simple extends BulkAwareDocumentListener {
    @Override
    default void beforeDocumentChangeNonBulk(@NotNull DocumentEvent event) {
      beforeDocumentChange(event.getDocument());
    }

    @Override
    default void documentChangedNonBulk(@NotNull DocumentEvent event) {
      afterDocumentChange(event.getDocument());
    }

    @Override
    default void bulkUpdateStarting(@NotNull Document document) {
      beforeDocumentChange(document);
    }

    @Override
    default void bulkUpdateFinished(@NotNull Document document) {
      afterDocumentChange(document);
    }

    default void beforeDocumentChange(@NotNull Document document) {}
    default void afterDocumentChange(@NotNull Document document) {}
  }
}
