package com.intellij.ide.hierarchy;

import com.intellij.lang.LanguageExtensionPoint;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * Implement this interface to provide hierarchy browsing actions (Type Hierarchy, Method Hierarchy,
 * Call Hierarchy) for a custom language.
 *
 * @author yole
 */
public interface HierarchyProvider {
  ExtensionPointName<LanguageExtensionPoint> TYPE_EP_NAME = ExtensionPointName.create("com.intellij.typeHierarchyProvider");
  ExtensionPointName<LanguageExtensionPoint> METHOD_EP_NAME = ExtensionPointName.create("com.intellij.methodHierarchyProvider");

  /**
   * Returns the element for which the hierarchy should be displayed.
   *
   * @param dataContext the data context for the action invocation.
   * @return the target element, or null if the action is not applicable in this context.
   */
  @Nullable
  PsiElement getTarget(DataContext dataContext);

  /**
   * Creates a browser for viewing the hierarchy of the specified element.
   *
   * @param target the element to view the hierarchy for.
   * @return the browser instance.
   */
  HierarchyBrowser createHierarchyBrowser(final PsiElement target);

  /**
   * Notifies that the toolwindow has been shown and the specified browser is currently being displayed.
   *
   * @param hierarchyBrowser the browser instance created by {@link #createHierarchyBrowser(com.intellij.psi.PsiElement)}.
   */
  void browserActivated(final HierarchyBrowser hierarchyBrowser);
}
