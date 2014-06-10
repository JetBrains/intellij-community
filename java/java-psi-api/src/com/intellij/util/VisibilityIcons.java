/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * @author max
 */
package com.intellij.util;

import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.util.PsiUtil;
import com.intellij.ui.RowIcon;
import com.intellij.util.ui.EmptyIcon;
import org.intellij.lang.annotations.MagicConstant;

import javax.swing.*;

public class VisibilityIcons {
  private VisibilityIcons() {}

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
        Icon emptyIcon = new EmptyIcon(PlatformIcons.PUBLIC_ICON.getIconWidth(), PlatformIcons.PUBLIC_ICON.getIconHeight());
        baseIcon.setIcon(emptyIcon, 1);
      }
    }
    else if (PlatformIcons.PUBLIC_ICON != null) {
        Icon emptyIcon = new EmptyIcon(PlatformIcons.PUBLIC_ICON.getIconWidth(), PlatformIcons.PUBLIC_ICON.getIconHeight());
        baseIcon.setIcon(emptyIcon, 1);
      }
  }

  public static void setVisibilityIcon(@MagicConstant(intValues = {PsiUtil.ACCESS_LEVEL_PUBLIC, PsiUtil.ACCESS_LEVEL_PROTECTED, PsiUtil.ACCESS_LEVEL_PACKAGE_LOCAL, PsiUtil.ACCESS_LEVEL_PRIVATE}) int accessLevel, RowIcon baseIcon) {
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
          icon = new EmptyIcon(PlatformIcons.PUBLIC_ICON.getIconWidth(), PlatformIcons.PUBLIC_ICON.getIconHeight());
        }
        else {
          return;
        }
    }
    baseIcon.setIcon(icon, 1);
  }
}
