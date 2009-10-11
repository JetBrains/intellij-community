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

package com.intellij.ide.util.frameworkSupport;

import com.intellij.facet.ui.libraries.LibraryInfo;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class FrameworkVersion {
  public static final FrameworkVersion[] EMPTY_ARRAY = new FrameworkVersion[0];
  private final String myVersionName;
  private final String myLibraryName;
  private final LibraryInfo[] myLibraries;
  private final boolean myDefault;

  public FrameworkVersion(String versionName) {
    this(versionName, false);
  }

  public FrameworkVersion(@NotNull String versionName, boolean isDefault) {
    myVersionName = versionName;
    myDefault = isDefault;
    myLibraryName = null;
    myLibraries = LibraryInfo.EMPTY_ARRAY;
  }

  public FrameworkVersion(String versionName, String libraryName, LibraryInfo[] libraries) {
    this(versionName, libraryName, libraries, false);
  }

  public FrameworkVersion(@NotNull String versionName, @NotNull String libraryName, @NotNull LibraryInfo[] libraries, boolean aDefault) {
    myVersionName = versionName;
    myLibraryName = libraryName;
    myLibraries = libraries;
    myDefault = aDefault;
  }

  public String getVersionName() {
    return myVersionName;
  }

  public String getLibraryName() {
    return myLibraryName;
  }

  public LibraryInfo[] getLibraries() {
    return myLibraries;
  }

  public boolean isDefault() {
    return myDefault;
  }
}
