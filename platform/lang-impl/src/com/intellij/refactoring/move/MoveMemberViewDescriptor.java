// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.move;

import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usageView.UsageViewDescriptor;
import org.jetbrains.annotations.NotNull;

public final class MoveMemberViewDescriptor implements UsageViewDescriptor {
  private final PsiElement[] myElementsToMove;

  public MoveMemberViewDescriptor(PsiElement[] elementsToMove) {
    myElementsToMove = elementsToMove;
  }

  @Override
  public PsiElement @NotNull [] getElements() {
    return myElementsToMove;
  }

  @Override
  public String getProcessedElementsHeader() {
    return RefactoringBundle.message("move.members.elements.header");
  }

  @Override
  public @NotNull String getCodeReferencesText(int usagesCount, int filesCount) {
    return RefactoringBundle.message("references.to.be.changed", UsageViewBundle.getReferencesString(usagesCount, filesCount));
  }
}
