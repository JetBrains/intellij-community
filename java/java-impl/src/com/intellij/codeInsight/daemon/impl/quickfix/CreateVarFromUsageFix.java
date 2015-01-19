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
 * User: mike
 * Date: Aug 14, 2002
 * Time: 5:15:42 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import org.jetbrains.annotations.Nls;

public abstract class CreateVarFromUsageFix extends CreateFromUsageBaseFix {
  protected final PsiReferenceExpression myReferenceExpression;

  public CreateVarFromUsageFix(PsiReferenceExpression referenceElement) {
    myReferenceExpression = referenceElement;
  }

  @Override
  protected boolean isValidElement(PsiElement element) {
    PsiReferenceExpression expression = (PsiReferenceExpression) element;
    return CreateFromUsageUtils.isValidReference(expression, false);
  }

  @Override
  protected boolean canBeTargetClass(PsiClass psiClass) {
    return false;
  }

  @Override
  protected PsiElement getElement() {
    if (!myReferenceExpression.isValid() || !myReferenceExpression.getManager().isInProject(myReferenceExpression)) return null;

    PsiElement parent = myReferenceExpression.getParent();

    if (parent instanceof PsiMethodCallExpression) return null;

    if (myReferenceExpression.getReferenceNameElement() != null) {
      if (!CreateFromUsageUtils.isValidReference(myReferenceExpression, false)) {
        return myReferenceExpression;
      }
    }

    return null;
  }

  @Override
  protected boolean isAvailableImpl(int offset) {
    if (CreateFromUsageUtils.shouldShowTag(offset, myReferenceExpression.getReferenceNameElement(), myReferenceExpression)) {
      setText(getText(myReferenceExpression.getReferenceName()));
      return true;
    }

    return false;
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  protected abstract String getText(String varName);
}
