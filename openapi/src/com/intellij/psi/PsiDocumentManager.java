/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;

import java.util.EventListener;

public abstract class PsiDocumentManager {
  public static PsiDocumentManager getInstance(Project project) {
    return project.getComponent(PsiDocumentManager.class);
  }

  public abstract PsiFile getPsiFile(Document document);
  public abstract PsiFile getCachedPsiFile(Document document);

  public abstract Document getDocument(PsiFile file);
  public abstract Document getCachedDocument(PsiFile file);

  public abstract void commitAllDocuments();
  public abstract void commitDocument(Document document);
  public abstract Document[] getUncommittedDocuments();
  public abstract boolean isUncommited(Document document);
  public abstract boolean hasUncommitedDocuments();
  public abstract void commitAndRunReadAction(Runnable runnable);
  public abstract <T> T commitAndRunReadAction(final Computable<T> computation);

  public interface Listener extends EventListener {
    void documentCreated(Document document, PsiFile psiFile);
    void fileCreated(PsiFile file, Document document);
  }

  public abstract void addListener(Listener listener);
  public abstract void removeListener(Listener listener);
}
