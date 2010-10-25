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
import com.intellij.openapi.roots.libraries.LibraryType;
import com.intellij.openapi.roots.libraries.scripting.ScriptingLibraryManager;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author Rustam Vishnyakov
 */
public class TypedLibraryTableWrapper {

  private Library[] myLibs;
  private boolean myIsUpdated;
  private ScriptingLibraryManager myLibraryManager;

  public TypedLibraryTableWrapper(ScriptingLibraryManager libraryManager) {
    myLibraryManager = libraryManager;
    myLibs = getLibraries();
    myIsUpdated = false;
  }

  private Library[] getLibraries() {
    List<Library> libs = new ArrayList<Library>();
    for (Iterator<Library> libIterator = myLibraryManager.getModelLibraryIterator(); libIterator != null && libIterator.hasNext();) {
      Library library = libIterator.next();
      if (library instanceof LibraryEx) {
        LibraryType libraryType = ((LibraryEx)library).getType();
        if (libraryType != null && libraryType.equals(myLibraryManager.getLibraryType())) {
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
    for (Library library : myLibs) {
      if (library.getName().equals(name) &&
          library instanceof LibraryEx &&
          ((LibraryEx)library).getType().equals(myLibraryManager.getLibraryType())) {
        return library;
      }
    }
    return null;
  }

  public boolean isUpdated() {
    return myIsUpdated;
  }
}
