/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.roots.libraries;

import java.util.EventListener;
import java.util.Iterator;

/**
 *  @author dsl
 */
public interface LibraryTable {
  Library[] getLibraries();

  Library createLibrary();

  Library createLibrary(String name);

  void removeLibrary(Library library);

  Iterator getLibraryIterator();

  Library getLibraryByName(String name);

  String getTableLevel();

  ModifiableModel getModifiableModel();

  void addListener(Listener listener);

  void removeListener(Listener listener);

  interface ModifiableModel {
    Library createLibrary(String name);
    void removeLibrary(Library library);

    void commit();

    Iterator getLibraryIterator();

    Library getLibraryByName(String name);

    Library[] getLibraries();

    boolean isChanged();
  }

  interface Listener extends EventListener{
    void afterLibraryAdded (Library newLibrary);
    void afterLibraryRenamed (Library library);
    void beforeLibraryRemoved (Library library);
    void afterLibraryRemoved (Library library);
  }
}
