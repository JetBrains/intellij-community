/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * @author peter
 */
public class FileContentUtil {
  private FileContentUtil() {
  }

  public static void setFileText(final @Nullable Project project, final VirtualFile virtualFile, final String text) throws IOException {
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
}
