/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.roots;

import com.intellij.openapi.roots.libraries.Library;

/**
 *  @author dsl
 */
public interface LibraryOrderEntry extends ExportableOrderEntry {
  Library getLibrary();
  boolean isModuleLevel();

  String getLibraryLevel();

  String getLibraryName();
}
