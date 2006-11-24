/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.deployment;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiDocumentManager;

import java.io.IOException;

/**
 * @author peter
 */
public class DeploymentItemUtil {
  public static void setFileText(final Project project, final VirtualFile childData, final String text) throws IOException {
    final PsiFile psiFile = PsiManager.getInstance(project).findFile(childData);
    final PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
    final Document document = psiFile == null? null : psiDocumentManager.getDocument(psiFile);
    if (document != null) {
      document.setText(text != null ? text : "");
      psiDocumentManager.commitDocument(document);
      FileDocumentManager.getInstance().saveDocument(document);
    }
    else {
      VfsUtil.saveText(childData, text != null ? text : "");
      childData.refresh(false, false);
    }
  }
}
