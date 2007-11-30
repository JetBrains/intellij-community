/*
 * @author max
 */
package com.intellij.util;

import com.intellij.ide.IconProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;
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

}