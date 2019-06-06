// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.light;

import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.ElementPresentationUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.ui.IconManager;
import com.intellij.ui.icons.RowIcon;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author ven
 */
public abstract class LightVariableBase extends LightElement implements PsiVariable, OriginInfoAwareElement {
  protected PsiElement myScope;
  protected PsiIdentifier myNameIdentifier;
  protected PsiType myType;
  protected final PsiModifierList myModifierList;
  protected boolean myWritable;
  private String myOriginInfo;

  public LightVariableBase(PsiManager manager, PsiIdentifier nameIdentifier, PsiType type, boolean writable, PsiElement scope) {
    this(manager, nameIdentifier, JavaLanguage.INSTANCE, type, writable, scope);
  }

  public LightVariableBase(PsiManager manager, PsiIdentifier nameIdentifier, Language language, PsiType type, boolean writable, PsiElement scope) {
    super(manager, language);
    myNameIdentifier = nameIdentifier;
    myWritable = writable;
    myType = type;
    myScope = scope;
    myModifierList = createModifierList();
  }

  protected PsiModifierList createModifierList() {
    return new LightModifierList(getManager());
  }

  @NotNull
  public PsiElement getDeclarationScope() {
    return myScope;
  }

  @Override
  public PsiIdentifier getNameIdentifier() {
    return myNameIdentifier;
  }

  @Override
  public boolean isValid() {
    return myNameIdentifier == null || myNameIdentifier.isValid();
  }

  @Override
  @NotNull
  public String getName() {
    return StringUtil.notNullize(getNameIdentifier().getText());
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException{
    PsiImplUtil.setName(getNameIdentifier(), name);
    return this;
  }

  @Override
  @NotNull
  public PsiType getType() {
    if (myType == null) {
      myType = computeType();
    }
    return myType;
  }

  @NotNull
  protected PsiType computeType() {
    return PsiType.VOID;
  }

  @Override
  @NotNull
  public PsiTypeElement getTypeElement() {
    return JavaPsiFacade.getElementFactory(getProject()).createTypeElement(myType);
  }

  @Override
  public PsiModifierList getModifierList() {
    return myModifierList;
  }

  @Override
  public boolean hasModifierProperty(@NotNull String name) {
    return getModifierList().hasModifierProperty(name);
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
  public String getText() {
    return myNameIdentifier.getText();
  }

  @Override
  public Object computeConstantValue() {
    return null;
  }

  @Override
  public void normalizeDeclaration() throws IncorrectOperationException {
  }

  @Override
  public boolean isWritable() {
    return myWritable;
  }
  @Override
  protected boolean isVisibilitySupported() {
    return true;
  }

  @Override
  public Icon getElementIcon(final int flags) {
    final RowIcon baseIcon =
      IconManager.getInstance().createLayeredIcon(this, PlatformIcons.VARIABLE_ICON, ElementPresentationUtil.getFlags(this, false));
    return ElementPresentationUtil.addVisibilityIcon(this, flags, baseIcon);
  }

  @Nullable
  @Override
  public String getOriginInfo() {
    return myOriginInfo;
  }

  public void setOriginInfo(String originInfo) {
    myOriginInfo = originInfo;
  }
}
