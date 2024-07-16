// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spi.psi;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.spi.SPIFileType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SPIPackageOrClassReferenceElement extends ASTWrapperPsiElement implements PsiReference {
  public SPIPackageOrClassReferenceElement(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public @NotNull PsiElement getElement() {
    return this;
  }

  @Override
  public @NotNull TextRange getRangeInElement() {
    final PsiElement last = PsiTreeUtil.getDeepestLast(this);
    return new TextRange(last.getStartOffsetInParent(), getTextLength());
  }

  @Override
  public @NotNull String getCanonicalText() {
    return getText();
  }

  @Override
  public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
    final SPIClassProvidersElementList firstChild =
      (SPIClassProvidersElementList)PsiFileFactory.getInstance(getProject())
        .createFileFromText("spi_dummy", SPIFileType.INSTANCE, newElementName).getFirstChild();
    PsiTreeUtil.getDeepestLast(this).replace(PsiTreeUtil.getDeepestLast(firstChild.getElements().get(0)));
    return this;
  }

  @Override
  public @Nullable PsiElement resolve() {
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
  public boolean isReferenceTo(@NotNull PsiElement element) {
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

  @Override
  public PsiReference @NotNull [] getReferences() {
    PsiReference[] references = ReferenceProvidersRegistry.getReferencesFromProviders(this);
    if (references.length > 0) return references;

    return super.getReferences();
  }
}
