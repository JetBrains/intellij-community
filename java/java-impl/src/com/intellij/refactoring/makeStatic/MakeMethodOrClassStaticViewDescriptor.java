// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.refactoring.makeStatic;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import org.jetbrains.annotations.NotNull;

public class MakeMethodOrClassStaticViewDescriptor implements UsageViewDescriptor {

  private final PsiMember myMember;
  private final @NlsContexts.ListItem String myProcessedElementsHeader;

  public MakeMethodOrClassStaticViewDescriptor(PsiMember member
  ) {
    myMember = member;
    String who = StringUtil.capitalize(UsageViewUtil.getType(myMember));
    myProcessedElementsHeader = JavaRefactoringBundle.message("make.static.elements.header", who);
  }

  @Override
  public PsiElement @NotNull [] getElements() {
    return new PsiElement[]{myMember};
  }


  @Override
  public String getProcessedElementsHeader() {
    return myProcessedElementsHeader;
  }

  @NotNull
  @Override
  public String getCodeReferencesText(int usagesCount, int filesCount) {
    return RefactoringBundle.message("references.to.be.changed", UsageViewBundle.getReferencesString(usagesCount, filesCount));
  }
}
