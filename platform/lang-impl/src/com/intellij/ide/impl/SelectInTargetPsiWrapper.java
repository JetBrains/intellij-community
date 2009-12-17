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

package com.intellij.ide.impl;

import com.intellij.ide.SelectInContext;
import com.intellij.ide.SelectInTarget;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;


public abstract class SelectInTargetPsiWrapper implements SelectInTarget {
  protected final Project myProject;

  protected SelectInTargetPsiWrapper(@NotNull final Project project) {
    myProject = project;
  }

  public abstract String toString();

  protected abstract boolean canSelect(PsiFileSystemItem file);

  public final boolean canSelect(SelectInContext context) {
    if (myProject.isDisposed()) return false;

    VirtualFile virtualFile = context.getVirtualFile();
    if (!virtualFile.isValid()) return false;

    final Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
    final PsiFileSystemItem psiFile;
    if (document != null) {
      psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
    }
    else if (context.getSelectorInFile() instanceof PsiFile) {
      psiFile = (PsiFile)context.getSelectorInFile();
    }
    else if (virtualFile.isDirectory()) {
      psiFile = PsiManager.getInstance(myProject).findDirectory(virtualFile);
    }
    else {
      psiFile = PsiManager.getInstance(myProject).findFile(virtualFile);
    }
    return psiFile != null && canSelect(psiFile) || canWorkWithCustomObjects();
  }

  public final void selectIn(SelectInContext context, final boolean requestFocus) {
    VirtualFile file = context.getVirtualFile();
    Object selector = context.getSelectorInFile();
    if (selector == null) {
      PsiManager psiManager = PsiManager.getInstance(myProject);
      selector = file.isDirectory() ? psiManager.findDirectory(file) : psiManager.findFile(file);
    }

    if (selector instanceof PsiElement) {
      select(((PsiElement)selector).getOriginalElement(), requestFocus);
    } else {
      select(selector, file, requestFocus);
    }
  }

  protected abstract void select(final Object selector, VirtualFile virtualFile, final boolean requestFocus);

  protected abstract boolean canWorkWithCustomObjects();

  protected abstract void select(PsiElement element, boolean requestFocus);
}
