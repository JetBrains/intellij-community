// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.openapi.progress.ProgressIndicatorProvider;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Represents a PSI element visitor which recursively visits the children of the element
 * on which the visit was started.
 */
public abstract class PsiRecursiveElementWalkingVisitor extends PsiElementVisitor implements PsiRecursiveVisitor {
  private final boolean myVisitAllFileRoots;
  private final PsiWalkingState myWalkingState = new PsiWalkingState(this){
    @Override
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

  @Override
  public void visitElement(final @NotNull PsiElement element) {
    ProgressIndicatorProvider.checkCanceled();

    myWalkingState.elementStarted(element);
  }

  protected void elementFinished(PsiElement element) {
                 
  }

  @Override
  public void visitFile(final @NotNull PsiFile psiFile) {
    if (myVisitAllFileRoots) {
      final FileViewProvider viewProvider = psiFile.getViewProvider();
      final List<PsiFile> allFiles = viewProvider.getAllFiles();
      if (allFiles.size() > 1) {
        if (psiFile == viewProvider.getPsi(viewProvider.getBaseLanguage())) {
          for (PsiFile lFile : allFiles) {
            lFile.acceptChildren(this);
          }
          return;
        }
      }
    }

    super.visitFile(psiFile);
  }

  public void stopWalking() {
    myWalkingState.stopWalking();
  }
}
