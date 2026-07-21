// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.elf;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows receiving notifications about changes in an ELF (editor lock-free) document.
 * <p>
 * An elf document is a lock-free view over a regular host document. Listener callbacks use the host document as the
 * document identity: {@link DocumentEvent#getDocument()} and bulk callback parameters point to the host document, not
 * to the elf view instance.
 * <p>
 * This listener is the elf-side counterpart of {@link com.intellij.openapi.editor.event.DocumentListener}. Regular document
 * changes are delivered through {@code beforeDocumentChange}/{@code documentChanged}; elf-side changes are delivered through
 * the callbacks declared here. Implementations shouldn't modify the corresponding document from listener methods.
 * <p>
 * Regular document changes are guarded by the global IDE read-write lock, but elf document changes are not. Listener
 * implementations must not rely on callbacks, including {@link #elfDocumentChanged(DocumentEvent, DocumentEvent)}, being protected by
 * read or write access, even if a particular callback path currently happens to run with write access. They also must not
 * try to acquire the global read-write lock from these callbacks.
 *
 * @see com.intellij.openapi.editor.event.DocumentListener
 * @see BulkAwareElfDocumentListener
 * @see com.intellij.openapi.editor.ex.ElfCandidate
 */
public interface ElfDocumentListener {
  /**
   * Called before the text of the elf document is changed.
   * <p>
   * If revertingEvent is not null, this call is about to revert a previously reported elf change; see
   * {@link #elfDocumentChanged} for details. The same event instance is later passed as revertedEvent to that call.
   *
   * @param event          the event containing the information about the change.
   * @param revertingEvent the event describing the previously reported elf change being reverted.
   */
  default void beforeElfDocumentChange(@NotNull DocumentEvent event, @Nullable DocumentEvent revertingEvent) {
  }

  /**
   * Called after the text of the elf document has been changed.
   * <p>
   * When revertedEvent is not null, this callback additionally reports that an elf document change has reverted a
   * previously reported elf change. This happens when an elf-side change is reconciled with the host document state
   * and listeners need both the reverted change and the replacement change to update derived state.
   *
   * @param event         the event containing the information about the change.
   * @param revertedEvent the event describing the previously reported elf change being reverted.
   */
  default void elfDocumentChanged(@NotNull DocumentEvent event, @Nullable DocumentEvent revertedEvent) {
  }

  /**
   * Notifies about elf {@link Document#isInBulkUpdate() bulk mode} being started.
   *
   * @param document the host document.
   */
  default void bulkElfUpdateStarting(@NotNull Document document) {
  }

  /**
   * Notifies about elf {@link Document#isInBulkUpdate() bulk mode} being finished.
   *
   * @param document the host document.
   */
  default void bulkElfUpdateFinished(@NotNull Document document) {
  }
}
