/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.jarRepository;

import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.LibraryDependencyScopeSuggester;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryDescription;
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties;

public class RepositoryLibraryDependencyScopeSuggester extends LibraryDependencyScopeSuggester {
  @Nullable
  @Override
  public DependencyScope getDefaultDependencyScope(@NotNull Library library) {
    if (!(library instanceof LibraryEx)) {
      return null;
    }
    LibraryEx libraryEx = (LibraryEx)library;
    LibraryProperties libraryProperties = libraryEx.getProperties();
    if (!(libraryProperties instanceof RepositoryLibraryProperties)) {
      return null;
    }
    RepositoryLibraryProperties repositoryLibraryProperties = (RepositoryLibraryProperties)libraryProperties;
    RepositoryLibraryDescription libraryDescription = RepositoryLibraryDescription.findDescription(repositoryLibraryProperties);

    return libraryDescription.getSuggestedScope();
  }
}
