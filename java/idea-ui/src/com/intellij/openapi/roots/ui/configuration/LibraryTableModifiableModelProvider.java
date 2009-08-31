package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablePresentation;

public interface LibraryTableModifiableModelProvider {

  LibraryTable.ModifiableModel getModifiableModel();

  String getTableLevel();

  LibraryTablePresentation getLibraryTablePresentation();

  boolean isLibraryTableEditable();
}
