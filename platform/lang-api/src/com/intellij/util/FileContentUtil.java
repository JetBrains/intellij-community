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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;

/**
 * @author peter
 */
public class FileContentUtil extends FileContentUtilCore {

  /**
   * @deprecated to be removed after IDEA 15. Use {@link VfsUtil#saveText(VirtualFile, String)} instead.
   */
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

  public static void reparseFiles(@NotNull final Project project, @NotNull final Collection<VirtualFile> files, final boolean includeOpenFiles) {
    LinkedHashSet<VirtualFile> fileSet = new LinkedHashSet<>(files);
    if (includeOpenFiles) {
      for (VirtualFile open : FileEditorManager.getInstance(project).getOpenFiles()) {
        if (!fileSet.contains(open)) {
          fileSet.add(open);
        }
      }
    }
    FileContentUtilCore.reparseFiles(fileSet);
  }

  public static void reparseOpenedFiles() {
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      reparseFiles(project, Collections.<VirtualFile>emptyList(), true);
    }
  }
}
