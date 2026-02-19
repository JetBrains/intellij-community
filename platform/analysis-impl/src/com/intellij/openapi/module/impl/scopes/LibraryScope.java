// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module.impl.scopes;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import org.jetbrains.annotations.NotNull;

public class LibraryScope extends LibraryScopeBase {
  private final Library myLibrary;
  private final String myLibraryName;

  public LibraryScope(Project project, Library library) {
    super(project, library.getFiles(OrderRootType.CLASSES), library.getFiles(OrderRootType.SOURCES));
    myLibraryName = library.getPresentableName();
    myLibrary = library;
  }

  @Override
  public @NotNull String getDisplayName() {
    return AnalysisBundle.message("library.0", myLibraryName);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    return myLibrary.equals(((LibraryScope)o).myLibrary);
  }

  @Override
  public int calcHashCode() {
    return 31 * super.calcHashCode() + myLibrary.hashCode();
  }

  public Library getLibrary() {
    return myLibrary;
  }
}
