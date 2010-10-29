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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.impl.libraries.LibraryTableBase;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.libraries.LibraryType;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

/**
 * @author Rustam Vishnyakov
 */
public class ScriptingLibraryManager {

  public enum LibraryLevel {GLOBAL, PROJECT}

  private Project myProject;
  private LibraryLevel myLibLevel = LibraryLevel.PROJECT;
  private ScriptingLibraryTable myLibTable;
  private LibraryType myLibraryType;

  public ScriptingLibraryManager(Project project, LibraryType libraryType) {
    this(LibraryLevel.GLOBAL, project, libraryType);
  }

  public ScriptingLibraryManager(LibraryLevel libLevel, Project project, LibraryType libraryType) {
    myProject = project;
    myLibLevel = libLevel;
    myLibraryType = libraryType;
  }

  public ScriptingLibraryTable getScriptingLibraryTable() {
    ensureModel();
    return myLibTable;
  }

  public void commitChanges() {
    if (myLibTable != null) {
      LibraryTable libTable = getLibraryTable();
      if (libTable != null) {
        LibraryTable.ModifiableModel libTableModel = libTable.getModifiableModel();
        updateLibraries(libTableModel, OrderRootType.SOURCES);
        updateLibraries(libTableModel, OrderRootType.CLASSES);
        libTableModel.commit();
      }
      myLibTable = null;
    }
    updateOpenProjects();
  }

  private static void updateOpenProjects() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
          ProjectRootManagerEx.getInstanceEx(project).makeRootsChange(EmptyRunnable.getInstance(), false, true);
        }
      }
    });
  }

  private void updateLibraries(LibraryTable.ModifiableModel libTableModel, OrderRootType rootType) {
    for (Library library : libTableModel.getLibraries()) {
      ScriptingLibraryTable.LibraryModel scriptingLibModel = myLibTable.getLibraryByName(library.getName());
      if (scriptingLibModel == null) {
        libTableModel.removeLibrary(library);
      }
      else {
        Library.ModifiableModel libModel = library.getModifiableModel();
        for (VirtualFile libRoot : libModel.getFiles(rootType)) {
          libModel.removeRoot(libRoot.getUrl(), rootType);
        }
        for (VirtualFile newRoot : scriptingLibModel.getFiles(rootType)) {
          libModel.addRoot(newRoot, rootType);
        }
        libModel.commit();
      }
    }
    for (ScriptingLibraryTable.LibraryModel scriptingLibModel : myLibTable.getLibraries()) {
      Library library = libTableModel.getLibraryByName(scriptingLibModel.getName());
      if (library == null && libTableModel instanceof LibraryTableBase.ModifiableModelEx) {
        library = ((LibraryTableBase.ModifiableModelEx)libTableModel).createLibrary(scriptingLibModel.getName(), myLibraryType);
        Library.ModifiableModel libModel = library.getModifiableModel();
        for (VirtualFile newRoot : scriptingLibModel.getFiles(rootType)) {
          libModel.addRoot(newRoot, rootType);
        }
        libModel.commit();
      }
    }
  }

  public void dropChanges() {
    myLibTable = null;
  }

  @Nullable
  public ScriptingLibraryTable.LibraryModel createLibrary(String name, VirtualFile[] sourceFiles, VirtualFile[] compactFiles) {
    if (ensureModel()) {
      return myLibTable.createLibrary(name, sourceFiles, compactFiles);
    }
    return null;
  }

  @Nullable
  public Library createSourceLibrary(String libName, String sourceUrl, LibraryLevel libraryLevel) {
    LibraryTable libraryTable = getLibraryTable(myProject, libraryLevel);
    if (libraryTable == null) return null;
    LibraryTable.ModifiableModel libTableModel = libraryTable.getModifiableModel();
    if (libTableModel instanceof LibraryTableBase.ModifiableModelEx) {
      Library library = ((LibraryTableBase.ModifiableModelEx)libTableModel).createLibrary(libName, myLibraryType);
      if (library != null) {
        Library.ModifiableModel libModel = library.getModifiableModel();
        libModel.addRoot(sourceUrl, OrderRootType.SOURCES);
        libModel.commit();
        libTableModel.commit();
        return library;
      }
    }
    return null;
  }

  public void removeLibrary(ScriptingLibraryTable.LibraryModel library) {
    if (ensureModel()) {
      myLibTable.removeLibrary(library);
    }
  }

  public void updateLibrary(String oldName, String name, VirtualFile[] sourceFiles, VirtualFile[] compactFiles) {
    if (ensureModel()) {
      ScriptingLibraryTable.LibraryModel libModel = myLibTable.getLibraryByName(oldName);
      if (libModel != null) {
        libModel.setName(name);
        libModel.setSourceFiles(sourceFiles);
        libModel.setCompactFiles(compactFiles);
      }
    }
  }

  @Nullable
  public ScriptingLibraryTable.LibraryModel[] getLibraries() {
    if (ensureModel()) {
      return myLibTable.getLibraries();
    }
    return null;
  }

  public boolean ensureModel() {
    if (myLibTable == null) {
      LibraryTable libTable = getLibraryTable();
      if (libTable != null) {
        myLibTable = new ScriptingLibraryTable(libTable, myLibraryType);
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

  public boolean isCompactFile(VirtualFile file) {
    ensureModel();
    assert myLibTable != null;
    return myLibTable.isCompactFile(file);
  }

}
