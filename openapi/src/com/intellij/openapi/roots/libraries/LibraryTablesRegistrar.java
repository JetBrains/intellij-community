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
package com.intellij.openapi.roots.libraries;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;

public abstract class LibraryTablesRegistrar {
  @SuppressWarnings({"HardCodedStringLiteral"})
  public static final String PROJECT_LEVEL = "project";
  @SuppressWarnings({"HardCodedStringLiteral"})
  public static final String APPLICATION_LEVEL = "application";

  public static LibraryTablesRegistrar getInstance() {
    return ApplicationManager.getApplication().getComponent(LibraryTablesRegistrar.class);
  }

  public abstract LibraryTable getLibraryTable();
  public abstract LibraryTable getLibraryTable(Project project);
  public abstract LibraryTable getLibraryTableByLevel(String level, Project project);
  public abstract void registerLibraryTable(LibraryTable libraryTable);
  public abstract LibraryTable registerLibraryTable(String customLevel);
}