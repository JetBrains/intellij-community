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
import org.jetbrains.annotations.Nullable;


public abstract class SelectInTargetPsiWrapper implements SelectInTarget {
  protected final Project myProject;

  protected SelectInTargetPsiWrapper(@NotNull final Project project) {
    myProject = project;
  }

  public abstract String toString();

  protected abstract boolean canSelect(PsiFileSystemItem file);

  @Override
  public final boolean canSelect(@NotNull SelectInContext context) {
    if (!isContextValid(context)) return false;

    return canWorkWithCustomObjects() || canSelectInner(context);
  }

  protected boolean canSelectInner(@NotNull SelectInContext context) {
    PsiFileSystemItem psiFile = getContextPsiFile(context);
    return psiFile != null && canSelect(psiFile);
  }

  private boolean isContextValid(SelectInContext context) {
    if (myProject.isDisposed()) return false;

    VirtualFile virtualFile = context.getVirtualFile();
    return virtualFile.isValid();
  }

  @Nullable
  protected PsiFileSystemItem getContextPsiFile(@NotNull SelectInContext context) {
    VirtualFile virtualFile = context.getVirtualFile();
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
    return psiFile;
  }

  @Override
  public final void selectIn(@NotNull SelectInContext context, boolean requestFocus) {
    VirtualFile file = context.getVirtualFile();
    Object selector = context.getSelectorInFile();
    if (selector == null) {
      PsiManager psiManager = PsiManager.getInstance(myProject);
      selector = file.isDirectory() ? psiManager.findDirectory(file) : psiManager.findFile(file);
    }

    if (selector instanceof PsiElement) {
      select(((PsiElement)selector).getOriginalElement(), requestFocus);
    }
    else {
      select(selector, file, requestFocus);
    }
  }

  protected abstract void select(Object selector, VirtualFile virtualFile, boolean requestFocus);

  protected abstract boolean canWorkWithCustomObjects();

  protected abstract void select(PsiElement element, boolean requestFocus);
}
