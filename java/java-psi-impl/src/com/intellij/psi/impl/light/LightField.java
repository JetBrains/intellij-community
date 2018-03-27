/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
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

  public LightField(@NotNull final PsiManager manager, @NotNull final PsiField field, @NotNull final PsiClass containingClass) {
    super(manager, JavaLanguage.INSTANCE);
    myField = field;
    myContainingClass = containingClass;
  }

  @Override
  public void setInitializer(@Nullable final PsiExpression initializer) throws IncorrectOperationException {
    throw new IncorrectOperationException("Not supported");
  }

  @NotNull
  @Override
  public SearchScope getUseScope() {
    return myField.getUseScope();
  }

  @Override
  public boolean isEquivalentTo(PsiElement another) {
    return PsiClassImplUtil.isFieldEquivalentTo(this, another);
  }

  @Override
  public String getName() {
    return myField.getName();
  }

  @NotNull
  @Override
  public PsiIdentifier getNameIdentifier() {
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

  @NotNull
  @Override
  public PsiType getType() {
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
  public PsiElement setName(@NonNls @NotNull final String name) throws IncorrectOperationException {
    throw new IncorrectOperationException("Not supported");
  }

  @Override
  public PsiModifierList getModifierList() {
    return myField.getModifierList();
  }

  @Override
  public boolean hasModifierProperty(@NonNls @NotNull final String name) {
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
