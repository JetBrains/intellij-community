// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Customize icons for {@link PsiElement}s.
 *
 * @see FileIconProvider
 * @author peter
 */
public abstract class IconProvider {
  public static final ExtensionPointName<IconProvider> EXTENSION_POINT_NAME = new ExtensionPointName<>("com.intellij.iconProvider");

  /**
   * @param element for which icon is shown
   * @param flags   used for customizing the icon appearance. Flags are listed in {@link Iconable}
   * @return {@code null} if this provider cannot provide icon for given element.
   * @see Iconable
   */
  @Nullable
  public abstract Icon getIcon(@NotNull PsiElement element, @Iconable.IconFlags int flags);
}
