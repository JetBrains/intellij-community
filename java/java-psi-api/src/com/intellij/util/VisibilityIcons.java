// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.util;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.util.PsiUtil;
import com.intellij.ui.icons.RowIcon;
import com.intellij.util.ui.EmptyIcon;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.ApiStatus;

import javax.swing.*;

public final class VisibilityIcons {
  private VisibilityIcons() {}

  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static void setVisibilityIcon(PsiModifierList modifierList, com.intellij.ui.RowIcon baseIcon) {
    setVisibilityIcon(modifierList, ((RowIcon)baseIcon));
  }

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
        Icon emptyIcon = EmptyIcon.create(PlatformIcons.PUBLIC_ICON);
        baseIcon.setIcon(emptyIcon, 1);
      }
    }
    else if (PlatformIcons.PUBLIC_ICON != null) {
        Icon emptyIcon = EmptyIcon.create(PlatformIcons.PUBLIC_ICON);
        baseIcon.setIcon(emptyIcon, 1);
      }
  }

  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static void setVisibilityIcon(@MagicConstant(intValues = {PsiUtil.ACCESS_LEVEL_PUBLIC, PsiUtil.ACCESS_LEVEL_PROTECTED, PsiUtil.ACCESS_LEVEL_PACKAGE_LOCAL, PsiUtil.ACCESS_LEVEL_PRIVATE}) int accessLevel, com.intellij.ui.RowIcon baseIcon) {
    setVisibilityIcon(accessLevel, ((RowIcon)baseIcon));
  }

  public static void setVisibilityIcon(@MagicConstant(intValues = {PsiUtil.ACCESS_LEVEL_PUBLIC, PsiUtil.ACCESS_LEVEL_PROTECTED, PsiUtil.ACCESS_LEVEL_PACKAGE_LOCAL, PsiUtil.ACCESS_LEVEL_PRIVATE}) int accessLevel, RowIcon baseIcon) {
    if (!Registry.is("ide.completion.show.visibility.icon")) return;

    Icon icon;
    switch (accessLevel) {
      case PsiUtil.ACCESS_LEVEL_PUBLIC:
        icon = PlatformIcons.PUBLIC_ICON;
        break;
      case PsiUtil.ACCESS_LEVEL_PROTECTED:
        icon = PlatformIcons.PROTECTED_ICON;
        break;
      case PsiUtil.ACCESS_LEVEL_PACKAGE_LOCAL:
        icon = PlatformIcons.PACKAGE_LOCAL_ICON;
        break;
      case PsiUtil.ACCESS_LEVEL_PRIVATE:
        icon = PlatformIcons.PRIVATE_ICON;
        break;
      default:
        if (PlatformIcons.PUBLIC_ICON != null) {
          icon = EmptyIcon.create(PlatformIcons.PUBLIC_ICON);
        }
        else {
          return;
        }
    }
    baseIcon.setIcon(icon, 1);
  }
}
