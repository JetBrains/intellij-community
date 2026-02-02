// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

/**
 * Customize icons for {@link PsiElement}s.
 *
 * @see FileIconProvider
 */
public abstract class IconProvider implements PossiblyDumbAware {
  public static final ExtensionPointName<IconProvider> EXTENSION_POINT_NAME = new ExtensionPointName<>("com.intellij.iconProvider");

  /**
   * @param element for which icon is shown
   * @param flags   used for customizing the icon appearance. Flags are listed in {@link Iconable}
   * @return {@code null} if this provider cannot provide icon for a given element.
   * @see Iconable
   */
  public abstract @Nullable Icon getIcon(@NotNull PsiElement element, @Iconable.IconFlags int flags);
}
