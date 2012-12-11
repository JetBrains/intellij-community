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

/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 16.04.2002
 * Time: 17:09:40
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.makeStatic;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.usageView.UsageInfo;

class InternalUsageInfo extends UsageInfo{
  private final PsiElement myReferencedElement;
  private Boolean myIsInsideAnonymous;

  InternalUsageInfo(PsiElement element, PsiElement referencedElement) {
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
}
