/*
 * @author max
 */
package com.intellij.util;

import com.intellij.ide.IconProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.util.PsiUtil;
import com.intellij.ui.RowIcon;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class PsiIconUtil {
  static IconProvider[] ourIconProviders = null;

  @Nullable
  public static Icon getProvidersIcon(PsiElement element, int flags) {
    for (final IconProvider iconProvider : getIconProviders()) {
      final Icon icon = iconProvider.getIcon(element, flags);
      if (icon != null) return icon;
    }
    return null;
  }

  static IconProvider[] getIconProviders() {
    if (ourIconProviders == null) {
      ourIconProviders = ApplicationManager.getApplication().getComponents(IconProvider.class);
    }
    return ourIconProviders;
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
        Icon emptyIcon = new EmptyIcon(Icons.PUBLIC_ICON.getIconWidth(), Icons.PUBLIC_ICON.getIconHeight());
        baseIcon.setIcon(emptyIcon, 1);
      }
    }
    else if (Icons.PUBLIC_ICON != null) {
        Icon emptyIcon = new EmptyIcon(Icons.PUBLIC_ICON.getIconWidth(), Icons.PUBLIC_ICON.getIconHeight());
        baseIcon.setIcon(emptyIcon, 1);
      }
  }

  static void setVisibilityIcon(int accessLevel, RowIcon baseIcon) {
    Icon icon;
    switch (accessLevel) {
      case PsiUtil.ACCESS_LEVEL_PUBLIC:
        icon = Icons.PUBLIC_ICON;
        break;
      case PsiUtil.ACCESS_LEVEL_PROTECTED:
        icon = Icons.PROTECTED_ICON;
        break;
      case PsiUtil.ACCESS_LEVEL_PACKAGE_LOCAL:
        icon = Icons.PACKAGE_LOCAL_ICON;
        break;
      case PsiUtil.ACCESS_LEVEL_PRIVATE:
        icon = Icons.PRIVATE_ICON;
        break;
      default:
        if (Icons.PUBLIC_ICON != null) {
          icon = new EmptyIcon(Icons.PUBLIC_ICON.getIconWidth(), Icons.PUBLIC_ICON.getIconHeight());
        }
        else {
          return;
        }
    }
    baseIcon.setIcon(icon, 1);
  }
}