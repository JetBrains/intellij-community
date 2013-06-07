/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.spi.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: anna
 */
public class SPIPackElement extends SPIProviderElement {
  public SPIPackElement(@NotNull ASTNode node) {
    super(node);
  }
  @Nullable
  @Override
  public PsiElement resolve() {
    PsiPackage aPackage = JavaPsiFacade.getInstance(getProject()).findPackage(getText());
    if (aPackage != null) {
      return aPackage;
    }
    return super.resolve();
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    if (element instanceof PsiPackage) {
      return handleElementRename(((PsiPackage)element).getQualifiedName());
    }
    return super.bindToElement(element);
  }


  @Override
  public boolean isReferenceTo(PsiElement element) {
    if (element instanceof PsiPackage) {
      return getText().equals(((PsiPackage)element).getQualifiedName());
    }
    return super.isReferenceTo(element);
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }
}
