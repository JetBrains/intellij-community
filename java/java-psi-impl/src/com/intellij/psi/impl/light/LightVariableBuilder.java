// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.light;

import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.navigation.NavigationItem;
import com.intellij.psi.*;
import com.intellij.psi.impl.ElementPresentationUtil;
import com.intellij.ui.IconManager;
import com.intellij.ui.icons.RowIcon;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author peter
 */
public class LightVariableBuilder<T extends LightVariableBuilder> extends LightElement implements PsiVariable, NavigationItem, OriginInfoAwareElement {
  private final String myName;
  private final PsiType myType;
  private volatile LightModifierList myModifierList;
  private volatile Icon myBaseIcon = PlatformIcons.VARIABLE_ICON;
  private String myOriginInfo;

  public LightVariableBuilder(@NotNull String name, @NotNull String type, @NotNull PsiElement navigationElement) {
    this(name, JavaPsiFacade.getElementFactory(navigationElement.getProject()).createTypeFromText(type, navigationElement), navigationElement);
  }

  public LightVariableBuilder(@NotNull String name, @NotNull PsiType type, @NotNull PsiElement navigationElement) {
    this(navigationElement.getManager(), name, type, JavaLanguage.INSTANCE);
    setNavigationElement(navigationElement);
  }

  public LightVariableBuilder(PsiManager manager, @NotNull String name, @NotNull PsiType type, @NotNull Language language) {
    super(manager, language);
    myName = name;
    myType = type;
    myModifierList = new LightModifierList(manager);
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

  public T setModifiers(String... modifiers) {
    myModifierList = new LightModifierList(getManager(), getLanguage(), modifiers);
    return (T)this;
  }

  public T setModifierList(LightModifierList modifierList) {
    myModifierList = modifierList;
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
