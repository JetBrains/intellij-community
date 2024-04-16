// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.compiled;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ClsReferenceExpressionImpl extends ClsElementImpl implements PsiReferenceExpression {
  private final ClsElementImpl myParent;
  private final PsiReferenceExpression myPatternExpression;
  private final PsiReferenceExpression myQualifier;
  private final String myName;
  private final PsiIdentifier myNameElement;

  public ClsReferenceExpressionImpl(ClsElementImpl parent, PsiReferenceExpression patternExpression) {
    myParent = parent;
    myPatternExpression = patternExpression;

    PsiReferenceExpression patternQualifier = (PsiReferenceExpression)myPatternExpression.getQualifierExpression();
    if (patternQualifier != null) {
      myQualifier = new ClsReferenceExpressionImpl(this, patternQualifier);
    }
    else {
      myQualifier = null;
    }

    myName = myPatternExpression.getReferenceName();
    myNameElement = new ClsIdentifierImpl(this, myName);
  }

  @Override
  public PsiElement getParent() {
    return myParent;
  }

  @Override
  public PsiExpression getQualifierExpression() {
    return myQualifier;
  }

  @Override
  public PsiElement bindToElementViaStaticImport(@NotNull PsiClass aClass) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public void setQualifierExpression(@Nullable PsiExpression newQualifier) throws IncorrectOperationException {
    throw new IncorrectOperationException("This method should not be called for compiled elements");
  }

  @Override
  public PsiElement getReferenceNameElement() {
    return myNameElement;
  }

  @Override
  public PsiReferenceParameterList getParameterList() {
    return null;
  }

  @Override
  public PsiElement @NotNull [] getChildren() {
    if (myQualifier != null) {
      return new PsiElement[]{myQualifier, myNameElement};
    }
    else {
      return new PsiElement[]{myNameElement};
    }
  }

  @Override
  public String getText() {
    return myQualifier != null ? myQualifier.getText() + "." + myName : myName;
  }

  @Override
  public boolean isQualified() {
    return myQualifier != null;
  }

  @Override
  public PsiType getType() {
    return myPatternExpression.getType();
  }

  @Override
  public PsiElement resolve() {
    return myPatternExpression.resolve();
  }

  @Override
  public @NotNull JavaResolveResult advancedResolve(boolean incompleteCode) {
    return myPatternExpression.advancedResolve(incompleteCode);
  }

  @Override
  public JavaResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
    final JavaResolveResult result = advancedResolve(incompleteCode);
    return result != JavaResolveResult.EMPTY ? new JavaResolveResult[]{result} : JavaResolveResult.EMPTY_ARRAY;
  }

  @Override
  public @NotNull PsiElement getElement() {
    return this;
  }

  @Override
  public @NotNull TextRange getRangeInElement() {
    return new TextRange(0, getTextLength());
  }

  @Override
  public @NotNull String getCanonicalText() {
    return myPatternExpression.getCanonicalText();
  }

  @Override
  public String getQualifiedName() {
    return getCanonicalText();
  }

  @Override
  public String getReferenceName() {
    return myPatternExpression.getReferenceName();
  }

  @Override
  public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public boolean isReferenceTo(@NotNull PsiElement element) {
    return myPatternExpression.isReferenceTo(element);
  }

  @Override
  public Object @NotNull [] getVariants() {
    return myPatternExpression.getVariants();
  }

  @Override
  public void processVariants(@NotNull PsiScopeProcessor processor) {
    myPatternExpression.processVariants(processor);
  }

  @Override
  public boolean isSoft() {
    return false;
  }

  @Override
  public void appendMirrorText(int indentLevel, @NotNull StringBuilder buffer) {
    buffer.append(getText());
  }

  @Override
  protected void setMirror(@NotNull TreeElement element) throws InvalidMirrorException {
    setMirrorCheckingType(element, JavaElementType.REFERENCE_EXPRESSION);
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
  public PsiType @NotNull [] getTypeParameters() {
    return PsiType.EMPTY_ARRAY;
  }

  @Override
  public PsiElement getQualifier() {
    return getQualifierExpression();
  }

  @Override
  public String toString() {
    return "PsiReferenceExpression:" + getText();
  }
}
