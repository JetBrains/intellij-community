/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
 *
 */

package com.intellij.facet.ui.libraries;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

/**
 * @author nik
 */
public class LibraryDownloadInfo {
  private final String myDownloadUrl;
  private final String myFileNamePrefix;
  private final String myFileNameSuffix;
  private final String myPresentableUrl;

  public LibraryDownloadInfo(final @NotNull String downloadUrl, final @Nullable String presentableUrl,
                                @NotNull @NonNls String fileNamePrefix, @NotNull @NonNls String fileNameSuffix) {
    myDownloadUrl = downloadUrl;
    myFileNamePrefix = fileNamePrefix;
    myFileNameSuffix = fileNameSuffix;
    myPresentableUrl = presentableUrl != null ? presentableUrl : downloadUrl;
  }

  public LibraryDownloadInfo(final @NotNull String downloadUrl, final @Nullable String presentableUrl,
                                @NotNull @NonNls String fileNamePrefix) {
    this(downloadUrl, presentableUrl, fileNamePrefix, ".jar");
  }

  public LibraryDownloadInfo(final @NotNull String downloadUrl, @NotNull @NonNls String fileNamePrefix) {
    this(downloadUrl, null, fileNamePrefix);
  }

  @NotNull
  public String getDownloadUrl() {
    return myDownloadUrl;
  }

  @NotNull
  public String getFileNamePrefix() {
    return myFileNamePrefix;
  }

  @NotNull
  public String getFileNameSuffix() {
    return myFileNameSuffix;
  }

  @NotNull
  public String getPresentableUrl() {
    return myPresentableUrl;
  }
}
