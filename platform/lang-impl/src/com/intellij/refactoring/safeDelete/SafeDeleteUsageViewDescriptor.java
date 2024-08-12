// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.safeDelete;

import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usageView.UsageViewDescriptor;
import org.jetbrains.annotations.NotNull;

public final class SafeDeleteUsageViewDescriptor implements UsageViewDescriptor {
  private final PsiElement @NotNull [] myElementsToDelete;

  SafeDeleteUsageViewDescriptor(PsiElement @NotNull [] elementsToDelete) {
    myElementsToDelete = elementsToDelete;
  }

  @Override
  public PsiElement @NotNull [] getElements() {
    return myElementsToDelete;
  }

  @Override
  public String getProcessedElementsHeader() {
    return RefactoringBundle.message("items.to.be.deleted");
  }

  @Override
  public @NotNull String getCodeReferencesText(int usagesCount, int filesCount) {
    return RefactoringBundle.message("references.in.code", UsageViewBundle.getReferencesString(usagesCount, filesCount));
  }

  @Override
  public String getCommentReferencesText(int usagesCount, int filesCount) {
    return RefactoringBundle.message("safe.delete.comment.occurences.header",
                                     UsageViewBundle.getOccurencesString(usagesCount, filesCount));
  }
}
