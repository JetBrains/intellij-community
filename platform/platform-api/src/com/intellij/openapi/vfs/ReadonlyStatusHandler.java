// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;

public abstract class ReadonlyStatusHandler {
  public static ReadonlyStatusHandler getInstance(@NotNull Project project) {
    return project.getService(ReadonlyStatusHandler.class);
  }

  public static boolean ensureFilesWritable(@NotNull Project project, VirtualFile @NotNull ... files) {
    return !getInstance(project).ensureFilesWritable(Arrays.asList(files)).hasReadonlyFiles();
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
    public abstract VirtualFile @NotNull [] getReadonlyFiles();

    public abstract boolean hasReadonlyFiles();

    @NotNull
    public abstract String getReadonlyFilesMessage();
  }

  /**
   * @deprecated Use {@link #ensureFilesWritable(Collection)}
   */
  @Deprecated
  @NotNull
  public OperationStatus ensureFilesWritable(VirtualFile @NotNull ... files) {
    return ensureFilesWritable(Arrays.asList(files));
  }

  @NotNull
  public abstract OperationStatus ensureFilesWritable(@NotNull Collection<? extends VirtualFile> files);
}
