// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.framework.library;

import com.intellij.framework.FrameworkTypeEx;
import com.intellij.framework.addSupport.FrameworkSupportInModuleProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.libraries.LibraryType;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public abstract class LibraryBasedFrameworkType extends FrameworkTypeEx {
  private static final Logger LOG = Logger.getInstance(LibraryBasedFrameworkType.class);
  private final Class<? extends DownloadableLibraryType> myLibraryTypeClass;

  protected LibraryBasedFrameworkType(@NotNull String id, Class<? extends DownloadableLibraryType> libraryTypeClass) {
    super(id);
    myLibraryTypeClass = libraryTypeClass;
  }

  protected Class<? extends DownloadableLibraryType> getLibraryTypeClass() {
    return myLibraryTypeClass;
  }

  @Override
  public @NotNull FrameworkSupportInModuleProvider createProvider() {
    return new LibraryBasedFrameworkSupportProvider(this, myLibraryTypeClass);
  }

  @Override
  public @NotNull Icon getIcon() {
    DownloadableLibraryType libraryType = getLibraryType();
    return libraryType.getLibraryTypeIcon();
  }

  public @NotNull DownloadableLibraryType getLibraryType() {
    DownloadableLibraryType libraryType = LibraryType.EP_NAME.findExtension(myLibraryTypeClass);
    LOG.assertTrue(libraryType != null, myLibraryTypeClass);
    return libraryType;
  }
}
