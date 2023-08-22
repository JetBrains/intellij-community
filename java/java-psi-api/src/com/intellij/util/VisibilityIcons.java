// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.util.PsiUtil;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import com.intellij.ui.icons.RowIcon;
import org.intellij.lang.annotations.MagicConstant;

import javax.swing.*;

public final class VisibilityIcons {
  private VisibilityIcons() { }

  public static void setVisibilityIcon(PsiModifierList modifierList, RowIcon baseIcon) {
    if (modifierList != null) {
      if (modifierList.hasModifierProperty(PsiModifier.PUBLIC)) {
        setVisibilityIcon(PsiUtil.ACCESS_LEVEL_PUBLIC, baseIcon);
      }
      else if (modifierList.hasModifierProperty(PsiModifier.PRIVATE)) {
        setVisibilityIcon(PsiUtil.ACCESS_LEVEL_PRIVATE, baseIcon);
      }
      else if (modifierList.hasModifierProperty(PsiModifier.PROTECTED)) {
        setVisibilityIcon(PsiUtil.ACCESS_LEVEL_PROTECTED, baseIcon);
      }
      else if (modifierList.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
        setVisibilityIcon(PsiUtil.ACCESS_LEVEL_PACKAGE_LOCAL, baseIcon);
      }
      else {
        Icon emptyIcon = IconManager.getInstance().createEmptyIcon(IconManager.getInstance().getPlatformIcon(PlatformIcons.Public));
        baseIcon.setIcon(emptyIcon, 1);
      }
    }
    else {
      Icon emptyIcon = IconManager.getInstance().createEmptyIcon(IconManager.getInstance().getPlatformIcon(PlatformIcons.Public));
      baseIcon.setIcon(emptyIcon, 1);
    }
  }

  public static void setVisibilityIcon(@MagicConstant(intValues = {PsiUtil.ACCESS_LEVEL_PUBLIC, PsiUtil.ACCESS_LEVEL_PROTECTED,
    PsiUtil.ACCESS_LEVEL_PACKAGE_LOCAL, PsiUtil.ACCESS_LEVEL_PRIVATE}) int accessLevel, RowIcon baseIcon) {
    Icon icon;
    IconManager iconManager = IconManager.getInstance();
    switch (accessLevel) {
      case PsiUtil.ACCESS_LEVEL_PUBLIC:
        icon = iconManager.getPlatformIcon(PlatformIcons.Public);
        break;
      case PsiUtil.ACCESS_LEVEL_PROTECTED:
        icon = iconManager.getPlatformIcon(PlatformIcons.Protected);
        break;
      case PsiUtil.ACCESS_LEVEL_PACKAGE_LOCAL:
        icon = iconManager.getPlatformIcon(PlatformIcons.Local);
        break;
      case PsiUtil.ACCESS_LEVEL_PRIVATE:
        icon = iconManager.getPlatformIcon(PlatformIcons.Private);
        break;
      default:
        icon = iconManager.createEmptyIcon(iconManager.getPlatformIcon(PlatformIcons.Public));
    }
    baseIcon.setIcon(icon, 1);
  }
}
