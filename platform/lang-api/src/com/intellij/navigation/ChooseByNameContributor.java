/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.navigation;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Allows a plugin to add items to "Goto Class" and "Goto Symbol" lists.
 *
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
   * @param includeNonProjectItems if true, the names of non-project items (for example,
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
   * @param includeNonProjectItems if true, the navigation items for non-project items (for example,
   *                               library classes) should be included in the returned array.
   * @return the array of navigation items.
   */
  @NotNull
  NavigationItem[] getItemsByName(String name, final String pattern, Project project, boolean includeNonProjectItems);
}