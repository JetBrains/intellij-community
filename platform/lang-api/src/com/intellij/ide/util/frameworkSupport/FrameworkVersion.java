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
