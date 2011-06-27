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
package com.intellij.ide.util.frameworkSupport;

import com.intellij.framework.library.DownloadableLibraryType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.libraries.LibraryType;
import com.intellij.openapi.roots.ui.configuration.libraries.CustomLibraryDescription;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class FrameworkVersionWithLibrary extends FrameworkVersion {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.frameworkSupport.FrameworkVersionWithLibrary");
  private CustomLibraryDescription myLibraryDescription;

  public FrameworkVersionWithLibrary(@NotNull String versionName, boolean isDefault, CustomLibraryDescription libraryDescription) {
    super(versionName, isDefault);
    myLibraryDescription = libraryDescription;
  }

  public CustomLibraryDescription getLibraryDescription() {
    return myLibraryDescription;
  }

  public static FrameworkVersionWithLibrary createVersion(Class<? extends DownloadableLibraryType> typeClass) {
    final DownloadableLibraryType libraryType = LibraryType.EP_NAME.findExtension(typeClass);
    LOG.assertTrue(libraryType != null, typeClass);
    CustomLibraryDescription description = new CustomLibraryDescriptionImpl(libraryType);
    return new FrameworkVersionWithLibrary("latest", true, description);
  }
}
