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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class FileDocumentManager {
  public static FileDocumentManager getInstance() {
    return ApplicationManager.getApplication().getComponent(FileDocumentManager.class);
  }

  @Nullable
  public abstract Document getDocument(@NotNull VirtualFile file);

  @Nullable
  public abstract Document getCachedDocument(@NotNull VirtualFile file);

  public abstract VirtualFile getFile(@NotNull Document document);

  public abstract void saveAllDocuments();
  public abstract void saveDocument(@NotNull Document document);
  @NotNull
  public abstract Document[] getUnsavedDocuments();
  public abstract boolean isDocumentUnsaved(@NotNull Document document);
  public abstract boolean isFileModified(@NotNull VirtualFile file);

  public abstract void addFileDocumentSynchronizationVetoer(@NotNull FileDocumentSynchronizationVetoListener vetoer);
  public abstract void removeFileDocumentSynchronizationVetoer(@NotNull FileDocumentSynchronizationVetoListener vetoer);

  /**
   * @deprecated Subscribe to {@link com.intellij.AppTopics#FILE_DOCUMENT_SYNC} on any level bus.
   */
  public abstract void addFileDocumentManagerListener(@NotNull FileDocumentManagerListener listener);

  /**
   * @deprecated Subscribe to {@link com.intellij.AppTopics#FILE_DOCUMENT_SYNC} on any level bus.
   */
  public abstract void removeFileDocumentManagerListener(@NotNull FileDocumentManagerListener listener);

  public abstract void reloadFromDisk(@NotNull Document document);

  @NotNull
  public abstract String getLineSeparator(@Nullable VirtualFile file, @Nullable Project project);

  /**
   * Requests writing access on given document, possibly involving interaction with user.
   *
   * @param document document
   * @param project project 
   * @return true if writing access allowed
   * @see com.intellij.openapi.vfs.ReadonlyStatusHandler#ensureFilesWritable(com.intellij.openapi.project.Project, com.intellij.openapi.vfs.VirtualFile...)
   */
  public abstract boolean requestWriting(@NotNull Document document, Project project);

  public static boolean fileForDocumentCheckedOutSuccessfully(@NotNull Document document, @NotNull Project project) {
    return getInstance().requestWriting(document, project);
  }

  public abstract void reloadFiles(VirtualFile... files);
}
