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
package com.intellij.openapi.roots.ui.configuration.packaging;

import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.impl.ui.LibraryElementPresentation;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class PackagingEditorUtil {
  private PackagingEditorUtil() {
  }

  public static String getLibraryItemText(final @NotNull Library library, final boolean includeTableName) {
    String name = library.getName();
    VirtualFile[] files = library.getFiles(OrderRootType.CLASSES);
    if (name != null) {
      return name + (includeTableName ? LibraryElementPresentation.getLibraryTableComment(library) : "");
    }
    else if (files.length > 0) {
      return files[0].getName() + (includeTableName ? LibraryElementPresentation.getLibraryTableComment(library) : "");
    }
    else {
      return ProjectBundle.message("library.empty.item");
    }
  }

}
