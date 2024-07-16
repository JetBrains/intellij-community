// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.light;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LightField extends LightElement implements PsiField {
  private final PsiField myField;
  private final PsiClass myContainingClass;

  public LightField(final @NotNull PsiManager manager, final @NotNull PsiField field, final @NotNull PsiClass containingClass) {
    super(manager, JavaLanguage.INSTANCE);
    myField = field;
    myContainingClass = containingClass;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitField(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public void setInitializer(final @Nullable PsiExpression initializer) throws IncorrectOperationException {
    throw new IncorrectOperationException("Not supported");
  }

  @Override
  public @NotNull SearchScope getUseScope() {
    return myField.getUseScope();
  }

  @Override
  public boolean isEquivalentTo(PsiElement another) {
    return PsiClassImplUtil.isFieldEquivalentTo(this, another);
  }

  @Override
  public @NotNull String getName() {
    return myField.getName();
  }

  @Override
  public @NotNull PsiIdentifier getNameIdentifier() {
    return myField.getNameIdentifier();
  }

  @Override
  public PsiDocComment getDocComment() {
    return myField.getDocComment();
  }

  @Override
  public boolean isDeprecated() {
    return myField.isDeprecated();
  }

  @Override
  public PsiClass getContainingClass() {
    return myContainingClass;
  }

  @Override
  public @NotNull PsiType getType() {
    return myField.getType();
  }

  @Override
  public PsiTypeElement getTypeElement() {
    return myField.getTypeElement();
  }

  @Override
  public PsiExpression getInitializer() {
    return myField.getInitializer();
  }

  @Override
  public boolean hasInitializer() {
    return myField.hasInitializer();
  }

  @Override
  public void normalizeDeclaration() throws IncorrectOperationException {
    throw new IncorrectOperationException("Not supported");
  }

  @Override
  public Object computeConstantValue() {
    return myField.computeConstantValue();
  }

  @Override
  public PsiElement setName(final @NonNls @NotNull String name) throws IncorrectOperationException {
    throw new IncorrectOperationException("Not supported");
  }

  @Override
  public PsiModifierList getModifierList() {
    return myField.getModifierList();
  }

  @Override
  public boolean hasModifierProperty(final @NonNls @NotNull String name) {
    return myField.hasModifierProperty(name);
  }

  @Override
  public String getText() {
    return myField.getText();
  }

  @Override
  public PsiElement copy() {
    return new LightField(myManager, (PsiField)myField.copy(), myContainingClass);
  }

  @Override
  public TextRange getTextRange() {
    return myField.getTextRange();
  }

  @Override
  public boolean isValid() {
    return myContainingClass.isValid();
  }

  @Override
  public String toString() {
    return "PsiField:" + getName();
  }
}
