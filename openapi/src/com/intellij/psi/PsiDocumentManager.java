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
