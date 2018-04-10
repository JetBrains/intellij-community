/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.spi.SPIFileType;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SPIPackageOrClassReferenceElement extends ASTWrapperPsiElement implements PsiReference {
  public SPIPackageOrClassReferenceElement(@NotNull ASTNode node) {
    super(node);
  }

  @NotNull
  @Override
  public PsiElement getElement() {
    return this;
  }

  @NotNull
  @Override
  public TextRange getRangeInElement() {
    final PsiElement last = PsiTreeUtil.getDeepestLast(this);
    return new TextRange(last.getStartOffsetInParent(), getTextLength());
  }

  @NotNull
  @Override
  public String getCanonicalText() {
    return getText();
  }

  @Override
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    final SPIClassProvidersElementList firstChild =
      (SPIClassProvidersElementList)PsiFileFactory.getInstance(getProject())
        .createFileFromText("spi_dummy", SPIFileType.INSTANCE, newElementName).getFirstChild();
    PsiTreeUtil.getDeepestLast(this).replace(PsiTreeUtil.getDeepestLast(firstChild.getElements().get(0)));
    return this;
  }

  @Nullable
  @Override
  public PsiElement resolve() {
    PsiPackage aPackage = JavaPsiFacade.getInstance(getProject()).findPackage(getText());
    if (aPackage != null) {
      return aPackage;
    }
    return ClassUtil.findPsiClass(getManager(), getText(), null, true, getResolveScope());
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    String newElementName;
    if (element instanceof PsiPackage) {
      newElementName = ((PsiPackage)element).getQualifiedName();
    }
    else if (element instanceof PsiClass) {
      newElementName = ClassUtil.getJVMClassName((PsiClass)element);
    }
    else {
      return null;
    }
    if (newElementName != null) {
      final SPIClassProvidersElementList firstChild =
        (SPIClassProvidersElementList)PsiFileFactory.getInstance(getProject())
          .createFileFromText("spi_dummy", SPIFileType.INSTANCE, newElementName).getFirstChild();
      return replace(firstChild.getElements().get(0));
    }
    return null;
  }

  @Override
  public boolean isReferenceTo(PsiElement element) {
    if (element instanceof PsiPackage) {
      return getText().equals(((PsiPackage)element).getQualifiedName());
    } else if (element instanceof PsiClass) {
      return getText().equals(ClassUtil.getJVMClassName((PsiClass)element));
    }
    return false;
  }

  @Override
  public boolean isSoft() {
    return false;
  }

  @Override
  public PsiReference getReference() {
    return this;
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }
}
