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
package com.intellij.refactoring.changeSignature;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.rename.UnresolvableCollisionUsageInfo;
import com.intellij.refactoring.util.RefactoringUIUtil;

/**
 *  @author dsl
 */
public class NewParameterCollidesWithLocalUsageInfo extends UnresolvableCollisionUsageInfo {
  private final PsiElement myConflictingElement;
  private final PsiMethod myMethod;

  public NewParameterCollidesWithLocalUsageInfo(PsiElement element, PsiElement referencedElement,
                                                PsiMethod method) {
    super(element, referencedElement);
    myConflictingElement = referencedElement;
    myMethod = method;
  }

  @Override
  public String getDescription() {
    return RefactoringBundle.message("there.is.already.a.0.in.1.it.will.conflict.with.the.new.parameter",
                                     RefactoringUIUtil.getDescription(myConflictingElement, true),
                                     RefactoringUIUtil.getDescription(myMethod, true));
  }
}
