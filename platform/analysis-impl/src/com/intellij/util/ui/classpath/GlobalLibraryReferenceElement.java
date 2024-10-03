// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.classpath;

import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@ApiStatus.Internal
public class GlobalLibraryReferenceElement implements SimpleClasspathElement {
  public static final @NonNls String NAME_ATTRIBUTE = "name";
  public static final @NonNls String LEVEL_ATTRIBUTE = "level";
  private final @NlsSafe String myLibraryName;

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
