// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.ide.IconProvider;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class PsiIconUtil {
  @Nullable
  public static Icon getProvidersIcon(@NotNull PsiElement element, @Iconable.IconFlags int flags) {
    for (IconProvider provider : DumbService.getDumbAwareExtensions(element.getProject(), IconProvider.EXTENSION_POINT_NAME)) {
      Icon icon = provider.getIcon(element, flags);
      if (icon != null) return icon;
    }
    return null;
  }
}