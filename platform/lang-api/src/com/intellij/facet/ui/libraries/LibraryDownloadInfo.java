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

import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class LibraryDownloadInfo {
  @Nullable private final RemoteRepositoryInfo myRemoteRepository;
  private final String myRelativeDownloadUrl;
  private final String myFileNamePrefix;
  private final String myFileNameSuffix;
  @Nullable private final String myPresentableUrl;

  public LibraryDownloadInfo(final @NotNull RemoteRepositoryInfo remoteRepository,
                             final @NotNull @NonNls String relativeDownloadUrl,
                             final @NotNull @NonNls String fileNamePrefix,
                             final @NotNull @NonNls String fileNameSuffix) {
    myRemoteRepository = remoteRepository;
    myRelativeDownloadUrl = relativeDownloadUrl;
    myFileNamePrefix = fileNamePrefix;
    myFileNameSuffix = fileNameSuffix;
    myPresentableUrl = null;
  }

  public LibraryDownloadInfo(final @NotNull String downloadUrl, final @Nullable String presentableUrl,
                                @NotNull @NonNls String fileNamePrefix, @NotNull @NonNls String fileNameSuffix) {
    myRemoteRepository = null;
    myRelativeDownloadUrl = downloadUrl;
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
    return myRemoteRepository != null ? getDownloadUrl(myRemoteRepository.getDefaultMirror()) : myRelativeDownloadUrl;
  }

  @NotNull
  public String getDownloadUrl(String mirror) {
    return mirror + myRelativeDownloadUrl;
  }

  @Nullable
  public RemoteRepositoryInfo getRemoteRepository() {
    return myRemoteRepository;
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
    return myPresentableUrl != null ? myPresentableUrl
           : myRemoteRepository != null ? myRemoteRepository.getDefaultMirror() : myRelativeDownloadUrl;
  }

  @NotNull
  public String getPresentableUrl(String mirror) {
    return myPresentableUrl != null ? myPresentableUrl : mirror;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final LibraryDownloadInfo that = (LibraryDownloadInfo)o;

    if (!myFileNamePrefix.equals(that.myFileNamePrefix)) return false;
    if (!myFileNameSuffix.equals(that.myFileNameSuffix)) return false;
    if (!Comparing.equal(myPresentableUrl, that.myPresentableUrl)) return false;
    if (!myRelativeDownloadUrl.equals(that.myRelativeDownloadUrl)) return false;
    if (!Comparing.equal(myRemoteRepository, that.myRemoteRepository)) return false;

    return true;
  }

  public int hashCode() {
    int result = myRemoteRepository != null ? myRemoteRepository.hashCode() : 0;
    result = 31 * result + (myRelativeDownloadUrl != null ? myRelativeDownloadUrl.hashCode() : 0);
    result = 31 * result + (myFileNamePrefix != null ? myFileNamePrefix.hashCode() : 0);
    result = 31 * result + (myFileNameSuffix != null ? myFileNameSuffix.hashCode() : 0);
    result = 31 * result + (myPresentableUrl != null ? myPresentableUrl.hashCode() : 0);
    return result;
  }
}
