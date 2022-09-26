// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.beanProperties;

import com.intellij.java.JavaBundle;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.FakePsiElement;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.meta.PsiPresentableMetaData;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class BeanPropertyElement extends FakePsiElement implements PsiMetaOwner, PsiPresentableMetaData {
  private final PsiMethod myMethod;
  private final String myName;

  public BeanPropertyElement(@NotNull PsiMethod method, @NotNull String name) {
    myMethod = method;
    myName = name;
  }

  public @Nullable PsiType getPropertyType() {
    return PropertyUtilBase.getPropertyType(myMethod);
  }

  public @NotNull PsiMethod getMethod() {
    return myMethod;
  }

  @Override
  public @NotNull PsiElement getNavigationElement() {
    return myMethod;
  }

  @Override
  public PsiManager getManager() {
    return myMethod.getManager();
  }

  @Override
  public PsiElement getDeclaration() {
    return this;
  }

  @Override
  public @NonNls String getName(PsiElement context) {
    return getName();
  }

  @Override
  public @NotNull String getName() {
    return myName;
  }

  @Override
  public void init(PsiElement element) {

  }

  @Override
  public @Nullable Icon getIcon(boolean flags) {
    return IconManager.getInstance().getPlatformIcon(PlatformIcons.Property);
  }

  @Override
  public PsiElement getParent() {
    return myMethod;
  }

  @Override
  public @Nullable PsiMetaData getMetaData() {
    return this;
  }

  @Override
  public String getTypeName() {
    return JavaBundle.message("bean.property");
  }

  @Override
  public @Nullable Icon getIcon() {
    return getIcon(0);
  }

  @Override
  public TextRange getTextRange() {
    return TextRange.from(0, 0);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    BeanPropertyElement element = (BeanPropertyElement)o;

    if (!myMethod.equals(element.myMethod)) return false;
    if (!myName.equals(element.myName)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myMethod.hashCode();
    result = 31 * result + myName.hashCode();
    return result;
  }
}
