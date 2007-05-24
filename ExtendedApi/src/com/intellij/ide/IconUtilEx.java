package com.intellij.ide;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.util.PsiUtil;
import com.intellij.ui.RowIcon;
import com.intellij.util.IconUtil;
import com.intellij.util.Icons;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.xml.ElementPresentationManager;

import javax.swing.*;

public class IconUtilEx {
  public static Icon getEmptyIcon(boolean showVisibility) {
    RowIcon baseIcon = new RowIcon(2);
    Icon emptyIcon = Icons.CLASS_ICON != null
                          ? new EmptyIcon(Icons.CLASS_ICON.getIconWidth(), Icons.CLASS_ICON.getIconHeight())
                          : null;
    baseIcon.setIcon(emptyIcon, 0);
    if (showVisibility) {
      emptyIcon = Icons.PUBLIC_ICON != null ? new EmptyIcon(Icons.PUBLIC_ICON.getIconWidth(), Icons.PUBLIC_ICON.getIconHeight()) : null;
      baseIcon.setIcon(emptyIcon, 1);
    }
    return baseIcon;
  }

  public static Icon getIcon(Object object, int flags, Project project) {
    if (object instanceof PsiElement) {
      return ((PsiElement)object).getIcon(flags);
    }
    if (object instanceof Module) {
      return getIcon((Module)object, flags);
    }
    if (object instanceof VirtualFile) {
      VirtualFile file = (VirtualFile)object;
      return IconUtil.getIcon(file, flags, project);
    }
    return ElementPresentationManager.getIcon(object);
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

  private static void setVisibilityIcon(int accessLevel, RowIcon baseIcon) {
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

  public static Icon getIcon(Module module, int flags) {
    return getModuleTypeIcon(module.getModuleType(), flags);
  }

  public static Icon getModuleTypeIcon(final ModuleType moduleType, int flags) {
    return moduleType.getNodeIcon((flags & Iconable.ICON_FLAG_OPEN) != 0);
  }

}