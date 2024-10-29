// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.move;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public final class MoveMultipleElementsViewDescriptor implements UsageViewDescriptor {
  private final PsiElement[] myPsiElements;
  private @NlsContexts.ListItem String myProcessedElementsHeader;
  private final @Nls String myCodeReferencesText;

  public MoveMultipleElementsViewDescriptor(PsiElement @NotNull [] psiElements, @NotNull String targetName) {
    myPsiElements = psiElements;
    if (psiElements.length == 1) {
      myProcessedElementsHeader = StringUtil.capitalize(
        RefactoringBundle.message("move.single.element.elements.header", UsageViewUtil.getType(psiElements[0]), targetName));
      myCodeReferencesText = RefactoringBundle
        .message("references.in.code.to.0.1", UsageViewUtil.getType(psiElements[0]), UsageViewUtil.getLongName(psiElements[0]));
    }
    else {
      if (psiElements.length > 0) {
        myProcessedElementsHeader = StringUtil.capitalize(
          RefactoringBundle
            .message("move.single.element.elements.header", StringUtil.pluralize(UsageViewUtil.getType(psiElements[0])), targetName));
      }
      myCodeReferencesText = RefactoringBundle.message("references.found.in.code");
    }
  }

  @Override
  public PsiElement @NotNull [] getElements() {
    return myPsiElements;
  }

  @Override
  public String getProcessedElementsHeader() {
    return myProcessedElementsHeader;
  }

  @Override
  public @NotNull String getCodeReferencesText(int usagesCount, int filesCount) {
    return myCodeReferencesText + UsageViewBundle.getReferencesString(usagesCount, filesCount);
  }

  @Override
  public String getCommentReferencesText(int usagesCount, int filesCount) {
    return RefactoringBundle.message("comments.elements.header",
                                UsageViewBundle.getOccurencesString(usagesCount, filesCount));
  }

}
