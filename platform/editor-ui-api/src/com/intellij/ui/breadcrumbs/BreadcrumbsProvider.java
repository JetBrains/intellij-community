// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.breadcrumbs;

import com.intellij.lang.Language;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Action;
import javax.swing.Icon;
import java.util.List;

import static java.util.Collections.emptyList;

/**
 * @author Alexey.Pegov
 * @author Sergey.Malenkov
 */
public interface BreadcrumbsProvider {
  ExtensionPointName<BreadcrumbsProvider> EP_NAME = ExtensionPointName.create("com.intellij.breadcrumbsInfoProvider");

  /**
   * @return an array of languages supported by this provider
   */
  Language[] getLanguages();

  /**
   * @param element that represents a single crumb
   * @return {@code true} if the specified element is supported by this provider
   */
  boolean acceptElement(@NotNull PsiElement element);

  /**
   * @param element that represents a single crumb
   * @return a text for the specified element
   */
  @NotNull
  String getElementInfo(@NotNull PsiElement element);

  /**
   * @param element that represents a single crumb
   * @return an icon for the specified element
   */
  @Nullable
  default Icon getElementIcon(@NotNull PsiElement element) {
    return null;
  }

  /**
   * @param element that represents a single crumb
   * @return a description for the specified element
   */
  @Nullable
  default String getElementTooltip(@NotNull PsiElement element) {
    return null;
  }

  /**
   * @param element that represents a single crumb
   * @return an element that represents a parent crumb, or {@code null}
   */
  @Nullable
  default PsiElement getParent(@NotNull PsiElement element) {
    return element.getParent();
  }

  /**
   * @param element that represents a single crumb
   * @return a list of elements to navigate
   */
  @NotNull
  default List<PsiElement> getChildren(@NotNull PsiElement element) {
    return emptyList();
  }

  /**
   * @param element that represents a single crumb
   * @return a list of actions for context menu
   */
  @NotNull
  default List<? extends Action> getContextActions(@NotNull PsiElement element) {
    return emptyList();
  }
}
