// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

public final class RepositoryLibraryDependencyScopeSuggester extends LibraryDependencyScopeSuggester {
  @Nullable
  @Override
  public DependencyScope getDefaultDependencyScope(@NotNull Library library) {
    if (!(library instanceof LibraryEx libraryEx)) {
      return null;
    }
    LibraryProperties<?> libraryProperties = libraryEx.getProperties();
    if (!(libraryProperties instanceof RepositoryLibraryProperties repositoryLibraryProperties)) {
      return null;
    }
    RepositoryLibraryDescription libraryDescription = RepositoryLibraryDescription.findDescription(repositoryLibraryProperties);

    return libraryDescription.getSuggestedScope();
  }
}
