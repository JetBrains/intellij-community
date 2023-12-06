// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.breadcrumbs;

import com.intellij.lang.Language;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

import static java.util.Collections.emptyList;

/**
 * Allows providing language-specific breadcrumbs, i.e. path to the file root from a selected PSI element.
 */
public interface BreadcrumbsProvider {
  ExtensionPointName<BreadcrumbsProvider> EP_NAME = ExtensionPointName.create("com.intellij.breadcrumbsInfoProvider");

  /**
   * @return array of languages supported by this provider
   */
  Language[] getLanguages();

  /**
   * @param element that represents a single crumb
   * @return {@code true} if this provider supports the specified element
   */
  boolean acceptElement(@NotNull PsiElement element);

  /**
   * Determines the text for a single crumb from the provided element.
   *
   * @param element that represents a single crumb
   * @return text for the crumb
   */
  @NotNull
  @NlsSafe String getElementInfo(@NotNull PsiElement element);

  /**
   * @param element that represents a single crumb
   * @return icon for the crumb
   */
  default @Nullable Icon getElementIcon(@NotNull PsiElement element) {
    return null;
  }

  /**
   * @param element that represents a single crumb
   * @return description for the crumb
   */
  default @Nullable @NlsSafe String getElementTooltip(@NotNull PsiElement element) {
    return null;
  }

  /**
   * @param element that represents a single crumb
   * @return element that represents a parent crumb, or {@code null}
   */
  default @Nullable PsiElement getParent(@NotNull PsiElement element) {
    return element.getParent();
  }

  /**
   * Reserved for future releases. Not supported yet.
   *
   * @param element that represents a single crumb
   * @return list of elements to navigate
   */
  default @NotNull List<PsiElement> getChildren(@NotNull PsiElement element) {
    return emptyList();
  }

  /**
   * @param element that represents a single crumb
   * @return list of actions for the context menu
   */
  default @NotNull List<? extends Action> getContextActions(@NotNull PsiElement element) {
    return emptyList();
  }

  /**
   * @return {@code false} if breadcrumbs for this provider should be hidden by default,
   * but it's possible to configure their visibility via Settings/Preferences | Editor | General | Breadcrumbs
   */
  default boolean isShownByDefault() {
    return true;
  }
}
