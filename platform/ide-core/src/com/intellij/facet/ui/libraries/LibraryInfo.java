// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.facet.ui.libraries;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public class LibraryInfo {
  public static final LibraryInfo[] EMPTY_ARRAY = new LibraryInfo[0];

  private final @Nullable LibraryDownloadInfo myDownloadInfo;
  private final @NonNls String myName;
  private @Nullable String myMd5;
  private final @NonNls String[] myRequiredClasses;

  public LibraryInfo(final @NonNls String name,
                     final @Nullable @NonNls String downloadingUrl,
                     final @Nullable String presentableUrl, final @Nullable String md5, final @NonNls String... requiredClasses) {
    myName = name;
    myMd5 = md5;
    myRequiredClasses = requiredClasses;
    if (downloadingUrl != null) {
      int dot = name.lastIndexOf('.');
      String prefix = name.substring(0, dot);
      String suffix = name.substring(dot);
      myDownloadInfo = new LibraryDownloadInfo(downloadingUrl, presentableUrl, prefix, suffix);
    }
    else {
      myDownloadInfo = null;
    }
  }

  public LibraryInfo(final @NonNls String name, final @Nullable LibraryDownloadInfo downloadInfo, String... requiredClasses) {
    myName = name;
    myRequiredClasses = requiredClasses;
    myDownloadInfo = downloadInfo;
  }

  public @NonNls String getName() {
    return myName;
  }

  public @NonNls String[] getRequiredClasses() {
    return myRequiredClasses;
  }

  public @Nullable LibraryDownloadInfo getDownloadingInfo() {
    return myDownloadInfo;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final LibraryInfo that = (LibraryInfo)o;

    if (myDownloadInfo != null ? !myDownloadInfo.equals(that.myDownloadInfo) : that.myDownloadInfo != null) return false;
    if (!myName.equals(that.myName)) return false;
    if (!Arrays.equals(myRequiredClasses, that.myRequiredClasses)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result;
    result = (myDownloadInfo != null ? myDownloadInfo.hashCode() : 0);
    result = 31 * result + myName.hashCode();
    result = 31 * result + Arrays.hashCode(myRequiredClasses);
    return result;
  }

  @Override
  public String toString() {
    return getName();
  }

  public @Nullable String getMd5() {
    return myMd5;
  }
}