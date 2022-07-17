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
package com.intellij.psi.impl.light;

import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

public class LightPackageReferenceExpression extends LightPackageReference implements PsiReferenceExpression {
  public LightPackageReferenceExpression(PsiManager manager, PsiPackage refPackage) {
    super(manager, refPackage);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitReferenceExpression(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public PsiExpression getQualifierExpression(){
    return null;
  }

  @Override
  public PsiElement bindToElementViaStaticImport(@NotNull PsiClass aClass) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public void setQualifierExpression(@Nullable PsiExpression newQualifier) throws IncorrectOperationException {
    throw new IncorrectOperationException("This method should not be called for light elements");
  }

  @Override
  public PsiType getType(){
    return null;
  }

  @Override
  public boolean isReferenceTo(@NotNull PsiElement element) {
    return getManager().areElementsEquivalent(resolve(), element);
  }
}
