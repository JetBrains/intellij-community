// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.elf;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Convenience interface for {@link ElfDocumentListener}-s which only process ELF text-change notifications outside
 * {@link Document#isInBulkUpdate() bulk mode}.
 * <p>
 * Bulk mode boundaries are still delivered through {@link #bulkElfUpdateStarting(Document)} and
 * {@link #bulkElfUpdateFinished(Document)}.
 */
public interface BulkAwareElfDocumentListener extends ElfDocumentListener {

  @Override
  default void beforeElfDocumentChange(@NotNull DocumentEvent event) {
    if (!event.getDocument().isInBulkUpdate()) {
      beforeElfDocumentChangeNonBulk(event);
    }
  }

  @Override
  default void elfDocumentChanged(@NotNull DocumentEvent event) {
    if (!event.getDocument().isInBulkUpdate()) {
      elfDocumentChangedNonBulk(event);
    }
  }

  @Override
  default void elfDocumentReverted(@NotNull DocumentEvent revertedEvent, @NotNull DocumentEvent event) {
    if (!revertedEvent.getDocument().isInBulkUpdate()) {
      elfDocumentRevertedNonBulk(revertedEvent, event);
    }
  }

  default void beforeElfDocumentChangeNonBulk(@NotNull DocumentEvent event) {
  }

  default void elfDocumentChangedNonBulk(@NotNull DocumentEvent event) {
  }

  default void elfDocumentRevertedNonBulk(@NotNull DocumentEvent revertedEvent, @NotNull DocumentEvent event) {
  }

  /**
   * Simple specialization of {@link BulkAwareElfDocumentListener} for the case when the listener doesn't need the details
   * of the changes (offsets and changed text), and is fine with receiving only one notification for changes done in bulk mode.
   */
  interface Simple extends BulkAwareElfDocumentListener {
    @Override
    default void beforeElfDocumentChangeNonBulk(@NotNull DocumentEvent event) {
      beforeElfDocumentChange(event.getDocument());
    }

    @Override
    default void elfDocumentChangedNonBulk(@NotNull DocumentEvent event) {
      afterElfDocumentChange(event.getDocument());
    }

    @Override
    default void elfDocumentRevertedNonBulk(@NotNull DocumentEvent revertedEvent, @NotNull DocumentEvent event) {
      afterElfDocumentRevert(revertedEvent.getDocument());
    }

    @Override
    default void bulkElfUpdateStarting(@NotNull Document document) {
      beforeElfDocumentChange(document);
    }

    @Override
    default void bulkElfUpdateFinished(@NotNull Document document) {
      afterElfDocumentChange(document);
    }

    @SuppressWarnings("unused")
    default void beforeElfDocumentChange(@NotNull Document document) {
    }

    @SuppressWarnings("unused")
    default void afterElfDocumentChange(@NotNull Document document) {
    }

    @SuppressWarnings("unused")
    default void afterElfDocumentRevert(@NotNull Document document) {
    }
  }
}
