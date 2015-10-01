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
package com.intellij.openapi.vfs;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public abstract class ReadonlyStatusHandler {
  public static boolean ensureFilesWritable(@NotNull Project project, @NotNull VirtualFile... files) {
    return !getInstance(project).ensureFilesWritable(files).hasReadonlyFiles();
  }

  public static boolean ensureDocumentWritable(@NotNull Project project, @NotNull Document document) {
    final PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
    boolean okWritable;
    if (psiFile == null) {
      okWritable = document.isWritable();
    }
    else {
      final VirtualFile virtualFile = psiFile.getVirtualFile();
      if (virtualFile != null) {
        okWritable = ensureFilesWritable(project, virtualFile);
      }
      else {
        okWritable = psiFile.isWritable();
      }
    }
    return okWritable;
  }

  public abstract static class OperationStatus {
    @NotNull
    public abstract VirtualFile[] getReadonlyFiles();

    public abstract boolean hasReadonlyFiles();

    @NotNull
    public abstract String getReadonlyFilesMessage();
  }

  public abstract OperationStatus ensureFilesWritable(@NotNull VirtualFile... files);

  public OperationStatus ensureFilesWritable(@NotNull Collection<VirtualFile> files) {
    return ensureFilesWritable(VfsUtilCore.toVirtualFileArray(files));
  }

  public static ReadonlyStatusHandler getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, ReadonlyStatusHandler.class);
  }

}
