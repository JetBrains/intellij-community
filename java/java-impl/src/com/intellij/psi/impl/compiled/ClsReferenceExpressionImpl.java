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
package com.intellij.psi.impl.compiled;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ClsReferenceExpressionImpl extends ClsElementImpl implements PsiReferenceExpression {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.compiled.ClsReferenceExpressionImpl");

  private final ClsElementImpl myParent;
  private final PsiReferenceExpression myPatternExpression;
  private final PsiReferenceExpression myQualifier;
  private final String myName;
  private final PsiIdentifier myNameElement;

  public ClsReferenceExpressionImpl(ClsElementImpl parent, PsiReferenceExpression patternExpression) {
    myParent = parent;
    myPatternExpression = patternExpression;

    PsiReferenceExpression patternQualifier = (PsiReferenceExpression)myPatternExpression.getQualifierExpression();
    if (patternQualifier != null){
      myQualifier = new ClsReferenceExpressionImpl(this, patternQualifier);
    }
    else{
      myQualifier = null;
    }

    myName = myPatternExpression.getReferenceName();
    myNameElement = new ClsIdentifierImpl(this, myName);
  }

  public PsiElement getParent() {
    return myParent;
  }

  public PsiExpression getQualifierExpression() {
    return myQualifier;
  }

  public PsiElement bindToElementViaStaticImport(@NotNull PsiClass aClass) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public void setQualifierExpression(@Nullable PsiExpression newQualifier) throws IncorrectOperationException {
    throw new IncorrectOperationException("This method should not be called for compiled elements");
  }

  public PsiElement getReferenceNameElement() {
    return myNameElement;
  }

  public PsiReferenceParameterList getParameterList() {
    return null;
  }

  @NotNull
  public PsiElement[] getChildren() {
    if (myQualifier != null){
      return new PsiElement[]{myQualifier, myNameElement};
    }
    else{
      return new PsiElement[]{myNameElement};
    }
  }

  public String getText() {
    return myQualifier != null ? myQualifier.getText() + "." + myName : myName;
  }

  public boolean isQualified() {
    return myQualifier != null;
  }

  public PsiType getType() {
    return myPatternExpression.getType();
  }

  public PsiElement resolve() {
    return myPatternExpression.resolve();
  }

  @NotNull
  public JavaResolveResult advancedResolve(boolean incompleteCode){
    return myPatternExpression.advancedResolve(incompleteCode);
  }

  @NotNull
  public JavaResolveResult[] multiResolve(boolean incompleteCode){
    final JavaResolveResult result = advancedResolve(incompleteCode);
    if(result != JavaResolveResult.EMPTY) return new JavaResolveResult[]{result};
    return JavaResolveResult.EMPTY_ARRAY;
  }


  public PsiElement getElement() {
    return this;
  }

  public TextRange getRangeInElement() {
    return new TextRange(0, getTextLength());
  }

  public String getCanonicalText() {
    return myPatternExpression.getCanonicalText();
  }

  public String getQualifiedName() {
    return getCanonicalText();
  }

  public String getReferenceName() {
    return myPatternExpression.getReferenceName();
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public boolean isReferenceTo(PsiElement element) {
    return myPatternExpression.isReferenceTo(element);
  }

  @NotNull
  public Object[] getVariants() {
    return myPatternExpression.getVariants();
  }

  public void processVariants(PsiScopeProcessor processor) {
    myPatternExpression.processVariants(processor);
  }

  public boolean isSoft() {
    return false;
  }

  public void appendMirrorText(final int indentLevel, final StringBuffer buffer) {
    buffer.append(getText());
  }

  public void setMirror(@NotNull TreeElement element) {
    setMirrorCheckingType(element, ElementType.REFERENCE_EXPRESSION);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitReferenceExpression(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiReferenceExpression:" + getText();
  }

  @NotNull
  public PsiType[] getTypeParameters() {
    return PsiType.EMPTY_ARRAY;
  }

  public PsiElement getQualifier() {
    return getQualifierExpression();
  }
}
