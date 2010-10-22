/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.roots.libraries.scripting;

import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.libraries.LibraryTableBase;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.libraries.LibraryType;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;

/**
 * @author Rustam Vishnyakov
 */
public class ScriptingLibraryManager {

  public enum LibraryLevel {GLOBAL, PROJECT}

  private Project myProject;
  private LibraryLevel myLibLevel = LibraryLevel.PROJECT;
  private LibraryTableBase.ModifiableModelEx myLibTableModel;
  private LibraryType myLibraryType;

  public ScriptingLibraryManager(Project project, LibraryType libraryType) {
    this(LibraryLevel.GLOBAL, project, libraryType);
  }

  public ScriptingLibraryManager(LibraryLevel libLevel, Project project, LibraryType libraryType) {
    myProject = project;
    myLibLevel = libLevel;
    myLibraryType = libraryType;
  }

  public void commitChanges() {
    if (myLibTableModel != null) {
      myLibTableModel.commit();
    }
    if (myLibLevel == LibraryLevel.GLOBAL) {
      ModuleManager.getInstance(myProject).getModifiableModel().commit();
    }
  }

  public void dropChanges() {
    myLibTableModel = null;
  }

  @Nullable
  public Library createLibrary(String name) {
    if (ensureModel()) {
      return myLibTableModel.createLibrary(name, myLibraryType);
    }
    return null;
  }

  public void removeLibrary(Library library) {
    if (ensureModel()) {
      myLibTableModel.removeLibrary(library);
    }
  }

  @Nullable
  public Iterator<Library> getModelLibraryIterator() {
    if (ensureModel()) {
      return myLibTableModel.getLibraryIterator();
    }
    return null;
  }

  public boolean ensureModel() {
    if (myLibTableModel == null) {
      LibraryTable libTable = getLibraryTable();
      if (libTable != null) {
        myLibTableModel = (LibraryTableBase.ModifiableModelEx)libTable.getModifiableModel();
        return true;
      }
      return false;
    }
    return true;
  }

  @Nullable
  public LibraryTable getLibraryTable() {
    return getLibraryTable(myProject, myLibLevel);
  }

  @Nullable
  public static LibraryTable getLibraryTable(Project project, LibraryLevel libraryLevel) {
    String libLevel = null;
    switch (libraryLevel) {
      case PROJECT:
        libLevel = LibraryTablesRegistrar.PROJECT_LEVEL;
        break;
      case GLOBAL:
        libLevel = LibraryTablesRegistrar.APPLICATION_LEVEL;
        break;
    }
    if (libLevel != null) {
      return LibraryTablesRegistrar.getInstance().getLibraryTableByLevel(libLevel, project);
    }
    return null;
  }

  public Project getProject() {
    return myProject;
  }

  public LibraryType getLibraryType() {
    return myLibraryType;
  }
}
