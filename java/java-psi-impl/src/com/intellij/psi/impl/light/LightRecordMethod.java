// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.light;

import com.intellij.model.BranchableSyntheticPsiElement;
import com.intellij.model.ModelBranch;
import com.intellij.psi.*;
import com.intellij.psi.impl.ElementPresentationUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import com.intellij.ui.icons.RowIcon;
import com.intellij.util.BitUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class LightRecordMethod extends LightMethod implements LightRecordMember, BranchableSyntheticPsiElement {
  private final @NotNull PsiRecordComponent myRecordComponent;
  private final @NotNull LightRecordComponentModifierList myModifierList;

  public LightRecordMethod(@NotNull PsiManager manager,
                           @NotNull PsiMethod method,
                           @NotNull PsiClass containingClass,
                           @NotNull PsiRecordComponent component) {
    super(manager, method, containingClass);
    myRecordComponent = component;
    myModifierList = new LightRecordComponentModifierList(this, method, component);
  }

  @Override
  @NotNull
  public PsiRecordComponent getRecordComponent() {
    return myRecordComponent;
  }

  @Override
  public int getTextOffset() {
    return myRecordComponent.getTextOffset();
  }

  @NotNull
  @Override
  public PsiElement getNavigationElement() {
    return myRecordComponent.getNavigationElement();
  }

  @Override
  public boolean isWritable() {
    return true;
  }

  @Override
  public PsiFile getContainingFile() {
    PsiClass containingClass = getContainingClass();
    return containingClass.getContainingFile();
  }

  @Override
  public PsiType getReturnType() {
    return myRecordComponent.getType();
  }

  @Override
  public @NotNull PsiModifierList getModifierList() {
    return myModifierList;
  }

  @Override
  public Icon getElementIcon(final int flags) {
    IconManager iconManager = IconManager.getInstance();
    RowIcon baseIcon =
      iconManager.createLayeredIcon(this, iconManager.getPlatformIcon(PlatformIcons.Method), ElementPresentationUtil.getFlags(this, false));
    if (BitUtil.isSet(flags, ICON_FLAG_VISIBILITY)) {
      VisibilityIcons.setVisibilityIcon(PsiUtil.ACCESS_LEVEL_PUBLIC, baseIcon);
    }
    return baseIcon;
  }

  @Override
  public @NotNull LightRecordMethod obtainBranchCopy(@NotNull ModelBranch branch) {
    PsiClass recordCopy = branch.obtainPsiCopy(myContainingClass);
    PsiMethod accessorCopy = recordCopy.findMethodBySignature(this, false);
    assert accessorCopy instanceof LightRecordMethod;
    return (LightRecordMethod)accessorCopy;
  }

  @Override
  public @Nullable ModelBranch getModelBranch() {
    return ModelBranch.getPsiBranch(myRecordComponent);
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    return myRecordComponent.setName(name);
  }

  @Override
  public PsiElement copy() {
    return myMethod.copy();
  }
}
