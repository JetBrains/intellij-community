// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

public interface FileDocumentManagerListener extends EventListener {

  /**
   * There is a possible case that callback that listens for the events implied by the current interface needs to modify document
   * contents (e.g. strip trailing spaces before saving a document). It's too dangerous to do that from message bus callback
   * because that may cause unexpected 'nested modification' (see IDEA-71701 for more details).
   * <p/>
   * That's why this interface is exposed via extension point as well - it's possible to modify document content from
   * the extension callback.
   */
  ExtensionPointName<FileDocumentManagerListener> EP_NAME = ExtensionPointName.create("com.intellij.fileDocumentManagerListener");

  /**
   * Fired before processing FileDocumentManager.saveAllDocuments(). Can be used by plugins
   * which need to perform additional save operations when documents, rather than settings,
   * are saved.
   *
   * @since 8.0
   */
  default void beforeAllDocumentsSaving() {
  }

  /**
   * NOTE: Vetoing facility is deprecated in this listener implement {@link FileDocumentSynchronizationVetoer} instead.
   */
  default void beforeDocumentSaving(@NotNull Document document) {
  }

  /**
   * NOTE: Vetoing facility is deprecated in this listener implement {@link FileDocumentSynchronizationVetoer} instead.
   */
  default void beforeFileContentReload(VirtualFile file, @NotNull Document document) {
  }

  default void fileWithNoDocumentChanged(@NotNull VirtualFile file) {
  }

  default void fileContentReloaded(@NotNull VirtualFile file, @NotNull Document document) {
  }

  default void fileContentLoaded(@NotNull VirtualFile file, @NotNull Document document) {
  }

  default void unsavedDocumentsDropped() {
  }
}
