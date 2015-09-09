/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.module.impl.scopes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class LibraryScope extends LibraryScopeBase {
  private final Library myLibrary;
  private final String myLibraryName;

  public LibraryScope(Project project, Library library) {
    super(project, library.getFiles(OrderRootType.CLASSES), library.getFiles(OrderRootType.SOURCES));
    myLibraryName = LibraryUtil.getPresentableName(library);
    myLibrary = library;
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return "Library '" + myLibraryName + "'";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    return myLibrary.equals(((LibraryScope)o).myLibrary);
  }

  @Override
  public int hashCode() {
    return 31 * super.hashCode() + myLibrary.hashCode();
  }
}
