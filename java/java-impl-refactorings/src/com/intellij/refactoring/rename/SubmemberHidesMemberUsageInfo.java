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

package com.intellij.refactoring.rename;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.usageView.UsageViewUtil;

public class SubmemberHidesMemberUsageInfo extends UnresolvableCollisionUsageInfo {
  public SubmemberHidesMemberUsageInfo(PsiElement element, PsiElement referencedElement) {
    super(element, referencedElement);
  }

  @Override
  public String getDescription() {
    String descr;
    if (!(getElement() instanceof PsiMethod)) {
      descr = JavaRefactoringBundle.message("0.will.hide.renamed.1",
                                        RefactoringUIUtil.getDescription(getElement(), true),
                                        UsageViewUtil.getType(getElement()));
    }
    else {
      descr = JavaRefactoringBundle.message("0.will.override.renamed.1",
                                        RefactoringUIUtil.getDescription(getElement(), true),
                                        UsageViewUtil.getType(getElement()));
    }
    return descr;
  }
}
