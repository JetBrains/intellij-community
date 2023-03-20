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

package com.intellij.refactoring.makeStatic;

import com.intellij.model.BranchableUsageInfo;
import com.intellij.model.ModelBranch;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

class InternalUsageInfo extends UsageInfo implements BranchableUsageInfo {
  final PsiElement myReferencedElement;
  private Boolean myIsInsideAnonymous;

  InternalUsageInfo(PsiElement element, @NotNull PsiElement referencedElement) {
    super(element);
    myReferencedElement = referencedElement;
    myIsInsideAnonymous = null;
    isInsideAnonymous();
  }

  public PsiElement getReferencedElement() {
    return myReferencedElement;
  }

  public boolean isInsideAnonymous() {
    if(myIsInsideAnonymous == null) {
      myIsInsideAnonymous = Boolean.valueOf(RefactoringUtil.isInsideAnonymousOrLocal(getElement(), null));
    }

    return myIsInsideAnonymous.booleanValue();
  }

  public boolean isWriting() {
    return myReferencedElement instanceof PsiField &&
              getElement() instanceof PsiReferenceExpression && PsiUtil.isAccessedForWriting(((PsiReferenceExpression)getElement()));
  }

  @Override
  public @NotNull UsageInfo obtainBranchCopy(@NotNull ModelBranch branch) {
    return new InternalUsageInfo(branch.obtainPsiCopy(Objects.requireNonNull(getElement())), 
                                 branch.obtainPsiCopy(myReferencedElement));
  }
}
