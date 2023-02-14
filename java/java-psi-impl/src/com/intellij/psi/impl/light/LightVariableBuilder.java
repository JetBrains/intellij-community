// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.light;

import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.navigation.NavigationItem;
import com.intellij.psi.*;
import com.intellij.psi.impl.ElementPresentationUtil;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import com.intellij.ui.icons.RowIcon;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class LightVariableBuilder<T extends LightVariableBuilder<?>> extends LightElement implements PsiVariable, NavigationItem, OriginInfoAwareElement {
  private final String myName;
  private final PsiType myType;
  private volatile LightModifierList myModifierList;
  private volatile Icon myBaseIcon = IconManager.getInstance().getPlatformIcon(PlatformIcons.Variable);
  private String myOriginInfo;

  public LightVariableBuilder(@NotNull String name, @NotNull String type, @NotNull PsiElement navigationElement) {
    this(name, JavaPsiFacade.getElementFactory(navigationElement.getProject()).createTypeFromText(type, navigationElement), navigationElement);
  }

  public LightVariableBuilder(@NotNull String name, @NotNull PsiType type, @NotNull PsiElement navigationElement) {
    this(navigationElement.getManager(), name, type, JavaLanguage.INSTANCE);
    setNavigationElement(navigationElement);
  }

  public LightVariableBuilder(PsiManager manager, @NotNull String name, @NotNull PsiType type, @NotNull Language language) {
    this(manager, name, type, language, new LightModifierList(manager));
  }

  public LightVariableBuilder(PsiManager manager, @NotNull String name, @NotNull PsiType type,
                              @NotNull Language language, @NotNull LightModifierList modifierList) {
    super(manager, language);
    myName = name;
    myType = type;
    myModifierList = modifierList;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitVariable(this);
    }
    else {
      visitor.visitElement(this);
    }
  }
  @Override
  public String toString() {
    return "LightVariableBuilder:" + getName();
  }

  @NotNull
  @Override
  public PsiType getType() {
    return myType;
  }

  @Override
  @NotNull
  public PsiModifierList getModifierList() {
    return myModifierList;
  }

  @NotNull
  public T setModifiers(@NotNull String @NotNull ... modifiers) {
    myModifierList = new LightModifierList(getManager(), getLanguage(), modifiers);
    //noinspection unchecked
    return (T)this;
  }

  @NotNull
  public T setModifierList(LightModifierList modifierList) {
    myModifierList = modifierList;
    //noinspection unchecked
    return (T)this;
  }

  @Override
  public boolean hasModifierProperty(@NonNls @NotNull String name) {
    return myModifierList.hasModifierProperty(name);
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @Override
  public PsiTypeElement getTypeElement() {
    return null;
  }

  @Override
  public PsiExpression getInitializer() {
    return null;
  }

  @Override
  public boolean hasInitializer() {
    return false;
  }

  @Override
  public void normalizeDeclaration() throws IncorrectOperationException {
  }

  @Override
  public Object computeConstantValue() {
    return null;
  }

  @Override
  public PsiIdentifier getNameIdentifier() {
    return null;
  }

  @Override
  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    throw new UnsupportedOperationException("setName is not implemented yet in com.intellij.psi.impl.light.LightVariableBuilder");
  }

  @Override
  protected boolean isVisibilitySupported() {
    return true;
  }

  @Override
  public Icon getElementIcon(final int flags) {
    final RowIcon baseIcon = IconManager.getInstance().createLayeredIcon(this, myBaseIcon, ElementPresentationUtil.getFlags(this, false));
    return ElementPresentationUtil.addVisibilityIcon(this, flags, baseIcon);
  }

  @SuppressWarnings("unchecked")
  public T setBaseIcon(Icon baseIcon) {
    myBaseIcon = baseIcon;
    return (T)this;
  }

  @Nullable
  @Override
  public String getOriginInfo() {
    return myOriginInfo;
  }

  public void setOriginInfo(@Nullable String originInfo) {
    myOriginInfo = originInfo;
  }
}
