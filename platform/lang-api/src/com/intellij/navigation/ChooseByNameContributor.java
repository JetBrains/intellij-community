// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.navigation;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Allows a plugin to add items to "Navigate Class|File|Symbol" lists.
 * <p>
 * Consider extending {@link ChooseByNameContributorEx} for better performance.
 *
 * @see com.intellij.navigation.ChooseByNameContributorEx
 * @see GotoClassContributor
 * @see ChooseByNameRegistry
 */
public interface ChooseByNameContributor {

  ExtensionPointName<ChooseByNameContributor> CLASS_EP_NAME = ExtensionPointName.create("com.intellij.gotoClassContributor");
  ExtensionPointName<ChooseByNameContributor> SYMBOL_EP_NAME = ExtensionPointName.create("com.intellij.gotoSymbolContributor");
  ExtensionPointName<ChooseByNameContributor> FILE_EP_NAME = ExtensionPointName.create("com.intellij.gotoFileContributor");

  /**
   * Returns the list of names for the specified project to which it is possible to navigate
   * by name.
   *
   * @param project                the project in which the navigation is performed.
   * @param includeNonProjectItems if {@code true}, the names of non-project items (for example,
   *                               library classes) should be included in the returned array.
   * @return the array of names.
   */
  @NotNull
  String[] getNames(Project project, boolean includeNonProjectItems);

  /**
   * Returns the list of navigation items matching the specified name.
   *
   * @param name                   the name selected from the list.
   * @param pattern                the original pattern entered in the dialog
   * @param project                the project in which the navigation is performed.
   * @param includeNonProjectItems if {@code true}, the navigation items for non-project items (for example,
   *                               library classes) should be included in the returned array.
   * @return the array of navigation items.
   */
  @NotNull
  NavigationItem[] getItemsByName(String name, final String pattern, Project project, boolean includeNonProjectItems);
}