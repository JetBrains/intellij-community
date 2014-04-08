/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
  void beforeAllDocumentsSaving();

  /**
   * NOTE: Vetoing facility is deprecated in this listener implement {@link FileDocumentSynchronizationVetoer} instead.
   */
  void beforeDocumentSaving(@NotNull Document document);

  /**
   * NOTE: Vetoing facility is deprecated in this listener implement {@link FileDocumentSynchronizationVetoer} instead.
   */
  void beforeFileContentReload(VirtualFile file, @NotNull Document document);

  void fileWithNoDocumentChanged(@NotNull VirtualFile file);

  void fileContentReloaded(@NotNull VirtualFile file, @NotNull Document document);

  void fileContentLoaded(@NotNull VirtualFile file, @NotNull Document document);

  void unsavedDocumentsDropped();
}
