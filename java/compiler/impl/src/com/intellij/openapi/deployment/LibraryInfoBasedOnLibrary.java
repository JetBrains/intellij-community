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

package com.intellij.openapi.deployment;

import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.util.InvalidDataException;
import org.jetbrains.annotations.NotNull;
import org.jdom.Element;

import java.util.List;
import java.util.Arrays;

/**
 * @author nik
*/
class LibraryInfoBasedOnLibrary implements LibraryInfo {
  private final Library myLibrary;
  private final LibraryInfoImpl myInfoToRestore;

  public LibraryInfoBasedOnLibrary(@NotNull Library library) {
    assert !(library instanceof LibraryEx) || !((LibraryEx)library).isDisposed();
    myLibrary = library;
    myInfoToRestore = new LibraryInfoImpl(library);
  }

  public String getName() {
    return myLibrary.getName();
  }

  @NotNull
  public List<String> getUrls() {
    return Arrays.asList(myLibrary.getUrls(OrderRootType.CLASSES));
  }

  public String getLevel() {
    final LibraryTable table = myLibrary.getTable();
    return table == null ? LibraryLink.MODULE_LEVEL : table.getTableLevel();
  }

  @NotNull
  public Library getLibrary() {
    return myLibrary;
  }

  public void readExternal(Element element) throws InvalidDataException {
  }

  public LibraryInfoImpl getInfoToRestore() {
    return myInfoToRestore;
  }
}
