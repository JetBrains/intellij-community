// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;

/**
 * @author peter
 */
public final class FileContentUtil {
  /**
   * @deprecated to be removed after IDEA 15. Use {@link VfsUtil#saveText(VirtualFile, String)} instead.
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2015")
  @Deprecated
  public static void setFileText(@Nullable Project project, final VirtualFile virtualFile, final String text) throws IOException {
    if (project == null) {
      project = ProjectUtil.guessProjectForFile(virtualFile);
    }
    if (project != null) {
      final PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
      final PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
      final Document document = psiFile == null? null : psiDocumentManager.getDocument(psiFile);
      if (document != null) {
        document.setText(text != null ? text : "");
        psiDocumentManager.commitDocument(document);
        FileDocumentManager.getInstance().saveDocument(document);
        return;
      }
    }
    VfsUtil.saveText(virtualFile, text != null ? text : "");
    virtualFile.refresh(false, false);
  }

  public static void reparseFiles(@NotNull final Project project, @NotNull final Collection<? extends VirtualFile> files, final boolean includeOpenFiles) {
    LinkedHashSet<VirtualFile> fileSet = new LinkedHashSet<>(files);
    if (includeOpenFiles) {
      Collections.addAll(fileSet, FileEditorManager.getInstance(project).getOpenFiles());
    }
    FileContentUtilCore.reparseFiles(fileSet);
  }

  public static void reparseOpenedFiles() {
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      reparseFiles(project, Collections.emptyList(), true);
    }
  }
}
