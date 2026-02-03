// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.hierarchy;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implement this interface to provide hierarchy browsing actions (Type Hierarchy, Method Hierarchy,
 * Call Hierarchy) for a custom language.
 */
public interface HierarchyProvider {
  /**
   * Returns the element for which the hierarchy should be displayed.
   *
   * @param dataContext the data context for the action invocation.
   * @return the target element, or null if the action is not applicable in this context.
   */
  @Nullable
  PsiElement getTarget(@NotNull DataContext dataContext);

  /**
   * Creates a browser for viewing the hierarchy of the specified element.
   *
   * @param target the element to view the hierarchy for.
   * @return the browser instance.
   */
  @NotNull
  HierarchyBrowser createHierarchyBrowser(@NotNull PsiElement target);

  /**
   * Notifies that the toolwindow has been shown and the specified browser is currently being displayed.
   *
   * @param hierarchyBrowser the browser instance created by {@link #createHierarchyBrowser(PsiElement)}.
   */
  void browserActivated(@NotNull HierarchyBrowser hierarchyBrowser);
}
