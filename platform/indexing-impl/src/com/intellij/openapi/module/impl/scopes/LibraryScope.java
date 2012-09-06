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
package com.intellij.openapi.module.impl.scopes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.util.PathUtil;

/**
 * @author nik
 */
public class LibraryScope extends LibraryScopeBase {
  private final Library myLibrary;

  public LibraryScope(Project project, Library library) {
    super(project, library.getFiles(OrderRootType.CLASSES), library.getFiles(OrderRootType.SOURCES));
    myLibrary = library;
  }

  @Override
  public String getDisplayName() {
    String name = myLibrary.getName();
    if (name == null) {
      String[] urls = myLibrary.getUrls(OrderRootType.CLASSES);
      if (urls.length > 0) {
        name = PathUtil.getFileName(VfsUtilCore.urlToPath(urls[0]));
      }
      else {
        name = "empty";
      }
    }
    return "Library '" + name + "'";
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
