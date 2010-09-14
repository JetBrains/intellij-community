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

package com.intellij.facet.ui.libraries;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * @author nik
 */
public class LibraryInfo {
  public static final LibraryInfo[] EMPTY_ARRAY = new LibraryInfo[0];

  private @Nullable final LibraryDownloadInfo myDownloadInfo;
  private @NonNls final String myName;
  private @NonNls final String[] myRequiredClasses;
  private boolean mySelected = true;

  public LibraryInfo(final @NonNls String name,
                     final @Nullable @NonNls String downloadingUrl,
                     final @Nullable String presentableUrl, final @NonNls String... requiredClasses) {
    myName = name;
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

  @NonNls
  public String getName() {
    return myName;
  }

  @NonNls
  public String[] getRequiredClasses() {
    return myRequiredClasses;
  }

  @Nullable
  public LibraryDownloadInfo getDownloadingInfo() {
    return myDownloadInfo;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final LibraryInfo that = (LibraryInfo)o;

    if (myDownloadInfo != null ? !myDownloadInfo.equals(that.myDownloadInfo) : that.myDownloadInfo != null) return false;
    if (!myName.equals(that.myName)) return false;
    if (!Arrays.equals(myRequiredClasses, that.myRequiredClasses)) return false;

    return true;
  }

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

  public boolean isSelected() {
    return mySelected;
  }

  public void setSelected(boolean selected) {
    mySelected = selected;
  }
}
