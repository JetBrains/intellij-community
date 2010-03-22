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
import com.intellij.openapi.vfs.VirtualFile;

import java.util.EventListener;

public interface FileDocumentManagerListener extends EventListener {
  /**
   * Fired before processing FileDocumentManager.saveAllDocuments(). Can be used by plugins
   * which need to perform additional save operations when documents, rather than settings,
   * are saved.
   *
   * @since 8.0
   */
  void beforeAllDocumentsSaving();

  /**
   * NOTE: Vetoing facility is deprecated in this listener implement {@link com.intellij.openapi.fileEditor.FileDocumentSynchronizationVetoListener} instead.
   */
  void beforeDocumentSaving(Document document);

  /**
   * NOTE: Vetoing facility is deprecated in this listener implement {@link com.intellij.openapi.fileEditor.FileDocumentSynchronizationVetoListener} instead.
   */
  void beforeFileContentReload(VirtualFile file, Document document);

  void fileWithNoDocumentChanged(VirtualFile file);
  void fileContentReloaded(VirtualFile file, Document document);
  void fileContentLoaded(VirtualFile file, Document document);

  void unsavedDocumentsDropped();
}
