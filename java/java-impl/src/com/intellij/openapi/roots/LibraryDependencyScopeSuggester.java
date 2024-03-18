// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.roots.libraries.Library;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows providing non default dependency scope for libraries e.g., 
 * test scope for testing libraries or provided scope for application server libraries
 */
public abstract class LibraryDependencyScopeSuggester {
  public static final ExtensionPointName<LibraryDependencyScopeSuggester> EP_NAME = ExtensionPointName.create("com.intellij.library.dependencyScopeSuggester");

  /**
   * Provides custom dependency scope. First extension which returns not null value wins, see {@link #getDefaultScope(Library)}.
   * 
   * @return custom scope or null if passed {@code library} isn't supported by the extension.
   */
  @Nullable
  @Contract(pure = true)
  public abstract DependencyScope getDefaultDependencyScope(@NotNull Library library);

  @NotNull
  public static DependencyScope getDefaultScope(@NotNull Library library) {
    for (LibraryDependencyScopeSuggester suggester : EP_NAME.getExtensionList()) {
      DependencyScope scope = suggester.getDefaultDependencyScope(library);
      if (scope != null) {
        return scope;
      }
    }
    return DependencyScope.COMPILE;
  }
}
