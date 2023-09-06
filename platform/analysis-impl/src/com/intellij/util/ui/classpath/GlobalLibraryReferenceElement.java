// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.classpath;

import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GlobalLibraryReferenceElement implements SimpleClasspathElement {
  @NonNls public static final String NAME_ATTRIBUTE = "name";
  @NonNls public static final String LEVEL_ATTRIBUTE = "level";
  @NlsSafe private final String myLibraryName;

  public GlobalLibraryReferenceElement(@NotNull String libraryName) {
    myLibraryName = libraryName;
  }

  public GlobalLibraryReferenceElement(@NotNull Element element) {
    myLibraryName = element.getAttributeValue(NAME_ATTRIBUTE);
  }

  @Override
  public String getPresentableName() {
    return myLibraryName;
  }

  public void writeExternal(Element element) {
    element.setAttribute(NAME_ATTRIBUTE, myLibraryName);
    //todo remote later. this is needed only for forward compatibility with version before 8
    element.setAttribute(LEVEL_ATTRIBUTE, LibraryTablesRegistrar.APPLICATION_LEVEL);
  }

  @Override
  public Library getLibrary() {
    final LibraryTable libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable();
    return libraryTable.getLibraryByName(myLibraryName);
  }

  @Override
  public String getLibraryName() {
    return myLibraryName;
  }

  @Override
  public void serialize(Element element) throws IOException {
    element.setAttribute(NAME_ATTRIBUTE, myLibraryName);
    //todo remote later. this is needed only for forward compatibility with version before 8
    element.setAttribute(LEVEL_ATTRIBUTE, LibraryTablesRegistrar.APPLICATION_LEVEL);
  }

  @Override
  public List<String> getClassesRootUrls() {
    final Library library = getLibrary();
    if (library != null) {
      final List<String> list = new ArrayList<>();
      for (VirtualFile file : library.getFiles(OrderRootType.CLASSES)) {
        list.add(file.getUrl());
      }
      return list;
    }
    return Collections.emptyList();
  }
}
