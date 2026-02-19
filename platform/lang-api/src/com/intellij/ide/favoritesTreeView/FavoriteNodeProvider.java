// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.favoritesTreeView;

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;

/**
 * Returns the nodes which should be added to the Favorites for the given data context.
 * Implementations of this class must be registered as extensions for
 * {@code com.intellij.favoriteNodeProvider} extension point.
 *
 * @deprecated Use Bookmarks API instead.
 */
@Deprecated(forRemoval = true)
public abstract class FavoriteNodeProvider {
  public static final ExtensionPointName<FavoriteNodeProvider> EP_NAME = new ExtensionPointName<>("com.intellij.favoriteNodeProvider");

  public abstract @Nullable @Unmodifiable Collection<AbstractTreeNode<?>> getFavoriteNodes(DataContext context, @NotNull ViewSettings viewSettings);

  public @Nullable AbstractTreeNode<?> createNode(final Project project, final Object element, @NotNull ViewSettings viewSettings) {
    return null;
  }

  /**
   * Checks if the specified project view node element (the value of {@link AbstractTreeNode}) contains
   * the specified virtual file as one of its children.
   *
   * @param element the value element of a project view node.
   * @param vFile   the file to check.
   * @return true if the file is contained, false if not or if {@code element} is not an element supported by this provider.
   */
  public abstract boolean elementContainsFile(final Object element, final VirtualFile vFile);

  /**
   * Returns the weight of the specified project view node element to use when sorting the favorites list.
   *
   * @param element the element for which the weight is requested.
   * @return the weight, or -1 if {@code element} is not an element supported by this provider.
   */
  public abstract int getElementWeight(final Object element, final boolean isSortByType);

  /**
   * Returns the location text (grey text in parentheses) to display in the Favorites view for the specified element.
   *
   * @param element the element for which the location is requested.
   * @return the location text, or -1 if {@code element} is not an element supported by this provider.
   */
  public abstract @Nullable @NlsSafe String getElementLocation(final Object element);

  /**
   * Checks if the specified element is invalid and needs to be removed from the tree.
   *
   * @param element the element to check.
   * @return true if the element is invalid, false if the element is valid or not supported by this provider.
   */
  public abstract boolean isInvalidElement(final Object element);

  /**
   * Returns the identifier used to persist favorites for this provider.
   *
   * @return the string identifier.
   */
  public abstract @NotNull @NonNls String getFavoriteTypeId();

  /**
   * Returns the persistable URL for the specified element.
   *
   * @return the URL, or null if the element is not supported by this provider.
   */
  public abstract @Nullable @NonNls String getElementUrl(final Object element);

  /**
   * Returns the name of the module containing the specified element.
   *
   * @return the name of the module, or null if the element is not supported by this provider or the module name is unknown.
   */
  public abstract @Nullable String getElementModuleName(final Object element);

  /**
   * Returns the path of node objects to be added to the favorites tree for the specified persisted URL and module name.
   *
   * @param project the project to which the favorite is related.
   * @param url the loaded URL (initially returned from {@link #getElementUrl }).
   * @param moduleName the name of the module containing the element (initially returned from {@link #getElementModuleName})
   * @return the path of objects to be added to the tree, or null if it was not possible to locate an object with the
   * specified URL.
   */
  public abstract Object @Nullable [] createPathFromUrl(final Project project, final String url, final String moduleName);

  public @Nullable PsiElement getPsiElement(final Object element) {
    if (element instanceof PsiElement) {
      return (PsiElement)element;
    }
    return null;
  }
}
