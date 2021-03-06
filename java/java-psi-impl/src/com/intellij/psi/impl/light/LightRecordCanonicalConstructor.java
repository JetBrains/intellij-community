// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.light;

import com.intellij.psi.*;
import com.intellij.psi.impl.ElementPresentationUtil;
import com.intellij.ui.IconManager;
import com.intellij.ui.icons.RowIcon;
import com.intellij.util.BitUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.VisibilityIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class LightRecordCanonicalConstructor extends LightMethod implements SyntheticElement {
  public LightRecordCanonicalConstructor(@NotNull PsiMethod method,
                                         @NotNull PsiClass containingClass) {
    super(method.getManager(), method, containingClass);
  }

  @Override
  public int getTextOffset() {
    return getNavigationElement().getTextOffset();
  }

  @NotNull
  @Override
  public PsiElement getNavigationElement() {
    return getContainingClass();
  }

  @Override
  public PsiFile getContainingFile() {
    PsiClass containingClass = getContainingClass();
    return containingClass.getContainingFile();
  }

  @Override
  public Icon getElementIcon(final int flags) {
    final RowIcon baseIcon =
      IconManager.getInstance().createLayeredIcon(this, PlatformIcons.METHOD_ICON, ElementPresentationUtil.getFlags(this, false));
    if (BitUtil.isSet(flags, ICON_FLAG_VISIBILITY)) {
      VisibilityIcons.setVisibilityIcon(getContainingClass().getModifierList(), baseIcon);
    }
    return baseIcon;
  }
}
