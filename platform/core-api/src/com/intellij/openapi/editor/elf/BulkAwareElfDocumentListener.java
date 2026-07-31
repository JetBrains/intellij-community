// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.elf;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Convenience interface for {@link ElfDocumentListener}-s which only process ELF text-change notifications outside
 * {@link Document#isInBulkUpdate() bulk mode}.
 * <p>
 * Bulk mode boundaries are still delivered through {@link #bulkElfUpdateStarting(Document)} and
 * {@link #bulkElfUpdateFinished(Document)}.
 */
public interface BulkAwareElfDocumentListener extends ElfDocumentListener {

  @Override
  default void beforeElfDocumentChange(@NotNull DocumentEvent event, @Nullable DocumentEvent revertingEvent) {
    if (!event.getDocument().isInBulkUpdate()) {
      beforeElfDocumentChangeNonBulk(event, revertingEvent);
    }
  }

  @Override
  default void elfDocumentChanged(@NotNull DocumentEvent event, @Nullable DocumentEvent revertedEvent) {
    if (!event.getDocument().isInBulkUpdate()) {
      elfDocumentChangedNonBulk(event, revertedEvent);
    }
  }

  default void beforeElfDocumentChangeNonBulk(@NotNull DocumentEvent event, @Nullable DocumentEvent revertingEvent) {
  }

  default void elfDocumentChangedNonBulk(@NotNull DocumentEvent event, @Nullable DocumentEvent revertedEvent) {
  }

  /**
   * Simple specialization of {@link BulkAwareElfDocumentListener} for the case when the listener doesn't need the details
   * of the changes (offsets and changed text), and is fine with receiving only one notification for changes done in bulk mode.
   */
  interface Simple extends BulkAwareElfDocumentListener {

    @Override
    default void beforeElfDocumentChangeNonBulk(@NotNull DocumentEvent event, @Nullable DocumentEvent revertingEvent) {
      beforeElfDocumentChange(event.getDocument());
    }

    @Override
    default void elfDocumentChangedNonBulk(@NotNull DocumentEvent event, @Nullable DocumentEvent revertedEvent) {
      afterElfDocumentChange(event.getDocument());
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
  }
}
