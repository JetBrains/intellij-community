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
package com.intellij.openapi.roots.ui.configuration.libraries;

import com.google.common.base.Predicate;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.impl.libraries.LibraryImpl;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesModifiableModel;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * @author nik
 */
public class LibraryEditingUtil {
  private LibraryEditingUtil() {
  }

  public static boolean libraryAlreadyExists(LibraryTable.ModifiableModel table, String libraryName) {
    for (Iterator<Library> it = table.getLibraryIterator(); it.hasNext(); ) {
      final Library library = it.next();
      final String libName;
      if (table instanceof LibrariesModifiableModel){
        libName = ((LibrariesModifiableModel)table).getLibraryEditor(library).getName();
      }
      else {
        libName = library.getName();
      }
      if (libraryName.equals(libName)) {
        return true;
      }
    }
    return false;
  }

  public static String suggestNewLibraryName(LibraryTable.ModifiableModel table) {
    return suggestNewLibraryName(table, "Unnamed");
  }

  public static String suggestNewLibraryName(LibraryTable.ModifiableModel table,
                                             final String baseName) {
    String candidateName = baseName;
    int idx = 1;
    while (libraryAlreadyExists(table, candidateName)) {
      candidateName = baseName + (idx++);
    }
    return candidateName;
  }

  public static Predicate<Library> getNotAddedLibrariesCondition(final ModuleRootModel rootModel) {
    final OrderEntry[] orderEntries = rootModel.getOrderEntries();
    final Set<Library> result = new HashSet<Library>(orderEntries.length);
    for (OrderEntry orderEntry : orderEntries) {
      if (orderEntry instanceof LibraryOrderEntry && orderEntry.isValid()) {
        final LibraryImpl library = (LibraryImpl)((LibraryOrderEntry)orderEntry).getLibrary();
        if (library != null) {
          final Library source = library.getSource();
          result.add(source != null ? source : library);
        }
      }
    }
    return new Predicate<Library>() {
      @Override
      public boolean apply(Library library) {
        if (result.contains(library)) return false;
        if (library instanceof LibraryImpl) {
          final Library source = ((LibraryImpl)library).getSource();
          if (source != null && result.contains(source)) return false;
        }
        return true;
      }
    };
  }
}
