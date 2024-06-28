// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PsiImportModuleReferenceElementImpl extends CompositePsiElement implements PsiImportModuleReferenceElement {
  public PsiImportModuleReferenceElementImpl() {
    super(JavaElementType.IMPORT_MODULE_REFERENCE);
  }

  @Override
  public int getTextOffset() {
    ASTNode refName = findChildByRole(ChildRole.REFERENCE_NAME);
    if (refName != null) {
      return refName.getStartOffset();
    }
    else {
      return super.getTextOffset();
    }
  }

  @Override
  public void clearCaches() {
    super.clearCaches();
  }

  @Override
  public PsiElement getReferenceNameElement() {
    return findChildByRoleAsPsiElement(ChildRole.REFERENCE_NAME);
  }

  @Override
  public PsiReferenceParameterList getParameterList() {
    return null;
  }

  @Override
  public PsiType @NotNull [] getTypeParameters() {
    return PsiType.EMPTY_ARRAY;
  }

  @Override
  public PsiElement getQualifier() {
    return findChildByRoleAsPsiElement(ChildRole.QUALIFIER);
  }

  @Override
  public @Nullable PsiJavaModuleReferenceElement getModuleReference() {
    return null;
  }

  @Override
  public boolean isQualified() {
    return findChildByRole(ChildRole.QUALIFIER) != null;
  }

  @Override
  public String getQualifiedName() {
    return getCanonicalText();
  }

  @Override
  public boolean isSoft() {
    return false;
  }

  @Override
  public String getReferenceName() {
    ASTNode childByRole = findChildByRole(ChildRole.REFERENCE_NAME);
    if (childByRole == null) return "";
    return childByRole.getText();
  }

  @Override
  public @NotNull PsiElement getElement() {
    return this;
  }

  @Override
  public @NotNull TextRange getRangeInElement() {
    TreeElement nameChild = (TreeElement)findChildByRole(ChildRole.REFERENCE_NAME);
    if (nameChild == null) return new TextRange(0, getTextLength());
    int startOffset = nameChild.getStartOffsetInParent();
    return new TextRange(startOffset, startOffset + nameChild.getTextLength());
  }

  @Override
  public @NotNull String getCanonicalText() {
    PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)getQualifier();
    if (referenceElement == null) {
      return getReferenceName();
    }
    else {
      return referenceElement.getCanonicalText();
    }
  }

  @Override
  public String toString() {
    return "PsiImportModuleReferenceElement:" + getText();
  }

  @Override
  public @NotNull JavaResolveResult advancedResolve(boolean incompleteCode) {
    JavaResolveResult[] results = multiResolve(incompleteCode);
    if (results.length == 1) return results[0];
    return JavaResolveResult.EMPTY;
  }

  @Override
  public JavaResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
    return JavaResolveResult.EMPTY_ARRAY;
  }

  @Override
  public PsiReference getReference() {
    return this;
  }

  @Override
  public PsiElement resolve() {
    return advancedResolve(false).getElement();
  }

  @Override
  public boolean isReferenceTo(@NotNull PsiElement element) {
    return false;
  }

  @Override
  public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
    return this;
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    return this;
  }

  @Override
  public void processVariants(@NotNull PsiScopeProcessor processor) {
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitImportModuleReferenceElement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }
}