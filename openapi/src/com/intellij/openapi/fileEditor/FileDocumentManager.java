/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

public abstract class FileDocumentManager {
  public static FileDocumentManager getInstance() {
    return ApplicationManager.getApplication().getComponent(FileDocumentManager.class);
  }

  public abstract Document getDocument(VirtualFile file);

  @Nullable
  public abstract Document getCachedDocument(VirtualFile file);

  public abstract VirtualFile getFile(Document document);

  public abstract void saveAllDocuments();
  public abstract void saveDocument(Document document);
  public abstract Document[] getUnsavedDocuments();
  public abstract boolean isDocumentUnsaved(Document document);
  public abstract boolean isFileModified(VirtualFile file);

  public abstract void addFileDocumentManagerListener(FileDocumentManagerListener listener);
  public abstract void removeFileDocumentManagerListener(FileDocumentManagerListener listener);
  public abstract void dispatchPendingEvents(FileDocumentManagerListener listener);

  public abstract void reloadFromDisk(Document document);

  public abstract String getLineSeparator(VirtualFile file, Project project);

  public static boolean fileForDocumentCheckedOutSuccessfully(final Document document, final Project project) {
    if (project != null) {
      final VirtualFile file = FileDocumentManager.getInstance().getFile(document);
      if (file != null) {
        final ReadonlyStatusHandler.OperationStatus operationStatus = ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(new VirtualFile[]{file});
        return !operationStatus.hasReadonlyFiles();
      } else {
        document.fireReadOnlyModificationAttempt();
        return false;
      }
    } else {
      document.fireReadOnlyModificationAttempt();
      return false;
    }

  }
}
