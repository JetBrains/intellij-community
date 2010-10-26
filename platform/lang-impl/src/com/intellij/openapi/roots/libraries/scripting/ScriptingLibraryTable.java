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

import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryType;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * @author Rustam Vishnyakov
 */
public class ScriptingLibraryTable {

  private final static OrderRootType SOURCE_ROOT_TYPE = OrderRootType.SOURCES;
  private final static OrderRootType COMPACT_ROOT_TYPE = OrderRootType.CLASSES;

  private ArrayList<LibraryModel> myLibraryModels = new ArrayList<LibraryModel>();

  public ScriptingLibraryTable(@NotNull LibraryTable libraryTable, LibraryType libraryType) {
    LibraryTable.ModifiableModel tableModel = libraryTable.getModifiableModel();
    for (Library library : tableModel.getLibraries()) {
      if (library instanceof LibraryEx) {
        LibraryType libType = ((LibraryEx)library).getType();
        if (libType != null && libType.equals(libraryType)) {
          LibraryModel libModel = new LibraryModel(library.getName());
          libModel.setSourceFiles(library.getFiles(SOURCE_ROOT_TYPE));
          libModel.setCompactFiles(library.getFiles(COMPACT_ROOT_TYPE));
          myLibraryModels.add(libModel);
        }
      }
    }
  }

  @Nullable
  public LibraryModel getLibraryByName(@NotNull String libName) {
    for (LibraryModel libraryModel : myLibraryModels) {
      if (libName.equals(libraryModel.getName())) return libraryModel;
    }
    return null;
  }

  public void removeLibrary(LibraryModel libraryModel) {
    myLibraryModels.remove(libraryModel);
  }

  public LibraryModel createLibrary(String libName) {
    LibraryModel libModel = new LibraryModel(libName);
    myLibraryModels.add(libModel);
    return libModel;
  }

  public LibraryModel createLibrary(String libName, VirtualFile[] sourceFiles, VirtualFile[] compactFiles) {
    LibraryModel libModel = new LibraryModel(libName, sourceFiles, compactFiles);
    myLibraryModels.add(libModel);
    return libModel;
  }

  public LibraryModel[] getLibraries() {
    return myLibraryModels.toArray(new LibraryModel[myLibraryModels.size()]);
  }

  public int getLibCount() {
    return myLibraryModels.size();
  }

  @Nullable
  public LibraryModel getLibraryAt(int index) {
    if (index < 0 || index > myLibraryModels.size() - 1) return null;
    return myLibraryModels.get(index);
  }

  public static class LibraryModel {
    private String myName;
    private ArrayList<VirtualFile> mySourceFiles = new ArrayList<VirtualFile>();
    private ArrayList<VirtualFile> myCompactFiles = new ArrayList<VirtualFile>();

    public LibraryModel(String name, VirtualFile[] sourceFiles, VirtualFile[] compactFiles) {
      this(name);
      mySourceFiles.addAll(Arrays.asList(sourceFiles));
      myCompactFiles.addAll(Arrays.asList(compactFiles));
    }

    public LibraryModel(String name) {
      myName = name;
    }

    public void setName(String name) {
      myName = name;
    }

    public void setSourceFiles(VirtualFile[] files) {
      mySourceFiles.clear();
      mySourceFiles.addAll(Arrays.asList(files));
    }

    public void setCompactFiles(VirtualFile[] files) {
      myCompactFiles.clear();
      myCompactFiles.addAll(Arrays.asList(files));
    }

    public VirtualFile[] getSourceFiles() {
      return mySourceFiles.toArray(new VirtualFile[mySourceFiles.size()]);
    }

    public VirtualFile[] getCompactFiles() {
      return myCompactFiles.toArray(new VirtualFile[myCompactFiles.size()]);
    }

    @NotNull
    public VirtualFile[] getFiles(OrderRootType rootType) {
      if (rootType == COMPACT_ROOT_TYPE) {
        return getCompactFiles();
      }
      return getSourceFiles();
    }

    public String getName() {
      return myName;
    }
  }
}
