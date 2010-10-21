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
package com.intellij.ide.scriptingContext.ui;

import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Rustam Vishnyakov
 */
public class TypedLibraryTableWrapper {

  private LibraryTable myLibraryTable;
  private LibraryType myLibraryType;
  private Library[] myLibs;
  private boolean myIsUpdated;

  public TypedLibraryTableWrapper(LibraryTable libraryTable, LibraryType libraryType) {
    myLibraryTable = libraryTable;
    myLibraryType = libraryType;
    myLibs = getLibraries();
    myIsUpdated = false;
  }

  private Library[] getLibraries() {
    List<Library> libs = new ArrayList<Library>();
    for (Library library : myLibraryTable.getLibraries()) {
      if (library instanceof LibraryEx) {
        LibraryType libraryType = ((LibraryEx)library).getType();
        if (libraryType != null && libraryType.equals(myLibraryType)) {
          libs.add(library);
        }
      }
    }
    return libs.toArray(new Library[libs.size()]);
  }

  public void update() {
    myLibs = getLibraries();
    myIsUpdated = true;
  }

  public int getLibCount() {
    return myLibs.length;
  }

  @Nullable
  public Library getLibraryAt(int index) {
    if (index >= 0 && index < myLibs.length) {
      return myLibs[index];
    }
    return null;
  }

  @Nullable
  public Library getLibraryByName(String name) {
    Library library = myLibraryTable.getLibraryByName(name);
    if (library instanceof  LibraryEx && ((LibraryEx)library).getType().equals(myLibraryType)) {
      return library;
    }
    return null;
  }

  public void removeLibrary(Library libToRemove) {
    myLibraryTable.removeLibrary(libToRemove);
  }

  public boolean isUpdated() {
    return myIsUpdated;
  }
}
