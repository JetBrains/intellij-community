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
package com.intellij.refactoring.convertToInstanceMethod;

import com.intellij.model.BranchableUsageInfo;
import com.intellij.model.ModelBranch;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;

/**
 * @author dsl
 */
final class ParameterUsageInfo extends UsageInfo implements BranchableUsageInfo {
  private final PsiReference myReferenceExpression;

  ParameterUsageInfo(PsiReference referenceElement) {
    super(referenceElement);
    myReferenceExpression = referenceElement;
  }

  public PsiReference getReferenceExpression() {
    return myReferenceExpression;
  }

  @Override
  public @NotNull UsageInfo obtainBranchCopy(@NotNull ModelBranch branch) {
    PsiElement element = getReferenceExpression().getElement();
    return new ParameterUsageInfo(branch.obtainPsiCopy(element).getReference());
  }
}
