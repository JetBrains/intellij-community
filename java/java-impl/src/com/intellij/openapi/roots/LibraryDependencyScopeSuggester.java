/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.roots;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.roots.libraries.Library;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public abstract class LibraryDependencyScopeSuggester {
  public static final ExtensionPointName<LibraryDependencyScopeSuggester> EP_NAME = ExtensionPointName.create("com.intellij.library.dependencyScopeSuggester");

  @Nullable
  public abstract DependencyScope getDefaultDependencyScope(@NotNull Library library);

  @NotNull
  public static DependencyScope getDefaultScope(@NotNull Library library) {
    for (LibraryDependencyScopeSuggester suggester : EP_NAME.getExtensions()) {
      DependencyScope scope = suggester.getDefaultDependencyScope(library);
      if (scope != null) {
        return scope;
      }
    }
    return DependencyScope.COMPILE;
  }
}
