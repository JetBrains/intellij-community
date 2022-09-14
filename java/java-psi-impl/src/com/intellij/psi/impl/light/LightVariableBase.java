// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.light;

import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.ElementPresentationUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import com.intellij.ui.icons.RowIcon;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

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

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitVariable(this);
    }
    else {
      visitor.visitElement(this);
    }
  }
  protected PsiModifierList createModifierList() {
    return new LightModifierList(getManager());
  }

  public @NotNull PsiElement getDeclarationScope() {
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
  public @NotNull @NlsSafe String getName() {
    return StringUtil.notNullize(getNameIdentifier().getText());
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException{
    PsiImplUtil.setName(getNameIdentifier(), name);
    return this;
  }

  @Override
  public @NotNull PsiType getType() {
    PsiType type = myType;
    if (type == null) {
      myType = type = computeType();
    }
    return type;
  }

  protected @NotNull PsiType computeType() {
    return PsiType.VOID;
  }

  @Override
  public @NotNull PsiTypeElement getTypeElement() {
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
    IconManager iconManager = IconManager.getInstance();
    RowIcon baseIcon = iconManager.createLayeredIcon(this, iconManager.getPlatformIcon(PlatformIcons.Variable),
                                                     ElementPresentationUtil.getFlags(this, false));
    return ElementPresentationUtil.addVisibilityIcon(this, flags, baseIcon);
  }

  @Override
  public @Nullable String getOriginInfo() {
    return myOriginInfo;
  }

  public void setOriginInfo(@NonNls String originInfo) {
    myOriginInfo = originInfo;
  }
}
