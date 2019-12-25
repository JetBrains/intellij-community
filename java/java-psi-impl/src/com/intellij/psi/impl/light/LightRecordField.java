// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.light;

import com.intellij.psi.*;
import com.intellij.psi.impl.ElementPresentationUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.ui.IconManager;
import com.intellij.ui.icons.RowIcon;
import com.intellij.util.BitUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.VisibilityIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class LightRecordField extends LightField implements LightRecordMember {
  private final @NotNull PsiRecordComponent myRecordComponent;

  public LightRecordField(@NotNull PsiManager manager,
                          @NotNull PsiField field,
                          @NotNull PsiClass containingClass,
                          @NotNull PsiRecordComponent component) {
    super(manager, field, containingClass);
    myRecordComponent = component;
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
    if (containingClass == null) return null;
    return containingClass.getContainingFile();
  }

  @Override
  public Icon getElementIcon(final int flags) {
    final RowIcon baseIcon =
      IconManager.getInstance().createLayeredIcon(this, PlatformIcons.FIELD_ICON, ElementPresentationUtil.getFlags(this, false));
    if (BitUtil.isSet(flags, ICON_FLAG_VISIBILITY)) {
      VisibilityIcons.setVisibilityIcon(PsiUtil.ACCESS_LEVEL_PRIVATE, baseIcon);
    }
    return baseIcon;
  }

  @Override
  public PsiElement getContext() {
    return getContainingClass();
  }
}
