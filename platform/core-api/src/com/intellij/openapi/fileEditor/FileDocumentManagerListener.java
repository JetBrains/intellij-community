// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

/**
 * Listener for {@link VirtualFile} to {@link Document} association events:
 * Virtual file loading, Document creation, Document saving back and reloading.
 */
public interface FileDocumentManagerListener extends EventListener {
  /**
   * There is a possible case that callback that listens for the events implied by the current interface needs to modify document
   * contents (e.g. strip trailing spaces before saving a document). It's too dangerous to do that from message bus callback
   * because that may cause unexpected 'nested modification' (see IDEA-71701 for more details).
   * <p/>
   * That's why this interface is exposed via extension point as well - it's possible to modify document content from
   * the extension callback.
   */
  ExtensionPointName<FileDocumentManagerListener> EP_NAME = new ExtensionPointName<>("com.intellij.fileDocumentManagerListener");

  @Topic.AppLevel
  Topic<FileDocumentManagerListener> TOPIC =
    new Topic<>(FileDocumentManagerListener.class, Topic.BroadcastDirection.TO_DIRECT_CHILDREN, true);

  /**
   * Fired before processing {@link FileDocumentManager#saveAllDocuments()}.
   * Can be used by plugins which need to perform additional save operations when documents are saved.
   */
  default void beforeAllDocumentsSaving() { }

  /**
   * Fired before processing {@link FileDocumentManager#saveDocument(Document)}.
   * Fired even document is unchanged.
   */
  default void beforeAnyDocumentSaving(@NotNull Document document, boolean explicit) { }

  /**
   * NOTE: Vetoing facility is deprecated in this listener implement {@link FileDocumentSynchronizationVetoer} instead.
   */
  default void beforeDocumentSaving(@NotNull Document document) { }

  /**
   * NOTE: Vetoing facility is deprecated in this listener implement {@link FileDocumentSynchronizationVetoer} instead.
   */
  default void beforeFileContentReload(@NotNull VirtualFile file, @NotNull Document document) { }

  default void fileWithNoDocumentChanged(@NotNull VirtualFile file) { }

  default void fileContentReloaded(@NotNull VirtualFile file, @NotNull Document document) { }

  default void fileContentLoaded(@NotNull VirtualFile file, @NotNull Document document) { }

  default void unsavedDocumentDropped(@NotNull Document document) {
    unsavedDocumentsDropped();
  }

  default void unsavedDocumentsDropped() { }

  default void afterDocumentUnbound(@NotNull VirtualFile file, @NotNull Document document) { }
}
