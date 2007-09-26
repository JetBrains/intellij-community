/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.deployment;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.List;


/**
 * @author Alexey Kudravtsev
 */
public abstract class LibraryLink extends ContainerElement {

  @NonNls public static final String MODULE_LEVEL = "module";

  public LibraryLink(Module parentModule) {
    super(parentModule);
  }

  public abstract @Nullable Library getLibrary();

  public abstract void addUrl(String url);

  public abstract List<String> getUrls();

  public abstract boolean hasDirectoriesOnly();

  public abstract @Nullable String getName();

  public abstract String getLevel();

  @Nullable
  public static Library findLibrary(String libraryName, String libraryLevel, Project project) {
    if (libraryName == null) {
      return null;
    }

    LibraryTable table = findTable(libraryLevel, project);
    if (table == null) {
      return null;
    }
    else {
      return table.getLibraryByName(libraryName);
    }
  }

  @Nullable
  protected static LibraryTable findTable(String libraryLevel, Project project) {
    if (libraryLevel == null) return null;
    if (LibraryTablesRegistrar.APPLICATION_LEVEL.equals(libraryLevel)) {
      return LibraryTablesRegistrar.getInstance().getLibraryTable();
    }
    return project == null? null : LibraryTablesRegistrar.getInstance().getLibraryTableByLevel(libraryLevel, project);
  }

}
