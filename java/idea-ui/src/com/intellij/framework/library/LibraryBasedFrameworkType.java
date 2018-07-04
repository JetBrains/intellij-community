/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.framework.library;

import com.intellij.framework.FrameworkTypeEx;
import com.intellij.framework.addSupport.FrameworkSupportInModuleProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.libraries.LibraryType;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author nik
 */
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

  @NotNull
  @Override
  public FrameworkSupportInModuleProvider createProvider() {
    return new LibraryBasedFrameworkSupportProvider(this, myLibraryTypeClass);
  }

  @NotNull
  @Override
  public Icon getIcon() {
    DownloadableLibraryType libraryType = getLibraryType();
    return libraryType.getLibraryTypeIcon();
  }

  @NotNull
  public DownloadableLibraryType getLibraryType() {
    DownloadableLibraryType libraryType = LibraryType.EP_NAME.findExtension(myLibraryTypeClass);
    LOG.assertTrue(libraryType != null, myLibraryTypeClass);
    return libraryType;
  }
}
