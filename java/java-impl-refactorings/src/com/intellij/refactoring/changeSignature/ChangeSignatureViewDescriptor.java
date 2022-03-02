// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.refactoring.changeSignature;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import org.jetbrains.annotations.NotNull;

public class ChangeSignatureViewDescriptor implements UsageViewDescriptor {

  private final PsiMethod myMethod;
  private final @NlsContexts.ListItem String myProcessedElementsHeader;

  public ChangeSignatureViewDescriptor(PsiMethod method) {
    myMethod = method;
    myProcessedElementsHeader = StringUtil.capitalize(RefactoringBundle.message("0.to.change.signature", UsageViewUtil.getType(method)));
  }

  @Override
  public PsiElement @NotNull [] getElements() {
    return new PsiElement[] {myMethod};
  }

  @Override
  public String getProcessedElementsHeader() {
    return myProcessedElementsHeader;
  }

  @NotNull
  @Override
  public String getCodeReferencesText(int usagesCount, int filesCount) {
    return RefactoringBundle.message("references.to.be.changed",
                                     UsageViewBundle.getReferencesString(usagesCount, filesCount));
  }
}
