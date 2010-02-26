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
package com.intellij.psi;

import com.intellij.openapi.progress.ProgressManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Represents a PSI element visitor which recursively visits the children of the element
 * on which the visit was started.
 */
public abstract class PsiRecursiveElementWalkingVisitor extends PsiElementVisitor  {
  private final boolean myVisitAllFileRoots;
  private final PsiWalkingState myWalkingState = new PsiWalkingState(this){
    public void elementFinished(@NotNull PsiElement element) {
      PsiRecursiveElementWalkingVisitor.this.elementFinished(element);
    }
  };

  protected PsiRecursiveElementWalkingVisitor() {
    this(false);
  }

  protected PsiRecursiveElementWalkingVisitor(boolean visitAllFileRoots) {
    myVisitAllFileRoots = visitAllFileRoots;
  }

  public void visitElement(final PsiElement element) {
    ProgressManager.checkCanceled();

    myWalkingState.elementStarted(element);
  }

  protected void elementFinished(PsiElement element) {
                 
  }

  @Override
  public void visitFile(final PsiFile file) {
    if (myVisitAllFileRoots) {
      final FileViewProvider viewProvider = file.getViewProvider();
      final List<PsiFile> allFiles = viewProvider.getAllFiles();
      if (allFiles.size() > 1) {
        if (file == viewProvider.getPsi(viewProvider.getBaseLanguage())) {
          for (PsiFile lFile : allFiles) {
            lFile.acceptChildren(this);
          }
          return;
        }
      }
    }

    super.visitFile(file);
  }
}
