// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.util;

import com.intellij.ide.IconProvider;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public class PsiIconUtil {
  @Nullable
  public static Icon getProvidersIcon(@NotNull PsiElement element, @Iconable.IconFlags int flags) {
    final boolean dumb = DumbService.getInstance(element.getProject()).isDumb();
    for (final IconProvider iconProvider : getIconProviders()) {
      if (dumb && !DumbService.isDumbAware(iconProvider)) {
        continue;
      }

      final Icon icon = iconProvider.getIcon(element, flags);
      if (icon != null) return icon;
    }
    return null;
  }

  private static class IconProviderHolder {
    private static final List<IconProvider> ourIconProviders = IconProvider.EXTENSION_POINT_NAME.getExtensionList();
  }

  @NotNull
  private static List<IconProvider> getIconProviders() {
    return IconProviderHolder.ourIconProviders;
  }
}