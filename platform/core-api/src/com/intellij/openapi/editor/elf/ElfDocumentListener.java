// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.elf;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Allows receiving notifications about changes in an ELF (editor lock-free) document.
 * <p>
 * An ELF document is a lock-free view over a regular host document. Listener callbacks use the host document as the
 * document identity: {@link DocumentEvent#getDocument()} and bulk callback parameters point to the host document, not
 * to the ELF view instance.
 * <p>
 * This listener is the ELF-side counterpart of {@link com.intellij.openapi.editor.event.DocumentListener}. Regular document
 * changes are delivered through {@code beforeDocumentChange}/{@code documentChanged}; ELF-side changes are delivered through
 * the callbacks declared here. Implementations shouldn't modify the corresponding document from listener methods.
 * <p>
 * Regular document changes are guarded by the global IDE read-write lock, but ELF document changes are not. Listener
 * implementations must not rely on callbacks, including {@link #elfDocumentChanged(DocumentEvent)}, being protected by
 * read or write access, even if a particular callback path currently happens to run with write access. They also must not
 * try to acquire the global read-write lock from these callbacks.
 *
 * @see com.intellij.openapi.editor.event.DocumentListener
 * @see BulkAwareElfDocumentListener
 * @see com.intellij.openapi.editor.ex.ElfCandidate
 */
public interface ElfDocumentListener {
  /**
   * Called before the text of the ELF document is changed.
   *
   * @param event the event containing the information about the change.
   */
  default void beforeElfDocumentChange(@NotNull DocumentEvent event) {
  }

  /**
   * Called after the text of the ELF document has been changed.
   *
   * @param event the event containing the information about the change.
   */
  default void elfDocumentChanged(@NotNull DocumentEvent event) {
  }

  /**
   * Called after an ELF document change has reverted a previously reported ELF change.
   * <p>
   * This happens when an ELF-side change is reconciled with the host document state and listeners need both the reverted
   * change and the replacement change to update derived state.
   *
   * @param revertedEvent the event describing the previously reported ELF change being reverted.
   * @param event the event describing the change applied instead.
   */
  default void elfDocumentReverted(@NotNull DocumentEvent revertedEvent, @NotNull DocumentEvent event) {
  }

  /**
   * Notifies about ELF {@link Document#isInBulkUpdate() bulk mode} being started.
   *
   * @param document the host document.
   */
  default void bulkElfUpdateStarting(@NotNull Document document) {
  }

  /**
   * Notifies about ELF {@link Document#isInBulkUpdate() bulk mode} being finished.
   *
   * @param document the host document.
   */
  default void bulkElfUpdateFinished(@NotNull Document document) {
  }
}
