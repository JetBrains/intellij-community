// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.framework.library.impl;

import com.intellij.framework.FrameworkAvailabilityCondition;
import com.intellij.framework.library.DownloadableLibraryFileDescription;
import com.intellij.framework.library.FrameworkLibraryVersion;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.download.impl.DownloadableFileSetDescriptionImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class FrameworkLibraryVersionImpl extends DownloadableFileSetDescriptionImpl<DownloadableLibraryFileDescription> implements FrameworkLibraryVersion {
  @Nullable private final String myLibraryName;
  @NotNull private final FrameworkAvailabilityCondition myAvailabilityCondition;
  private final String myLibraryCategory;

  public FrameworkLibraryVersionImpl(@Nullable String libraryName,
                                     @NotNull String versionString,
                                     @NotNull FrameworkAvailabilityCondition availabilityCondition,
                                     @NotNull List<? extends DownloadableLibraryFileDescription> libraryFiles,
                                     @NotNull String category) {
    super(category, versionString, libraryFiles);
    myLibraryName = libraryName;
    myAvailabilityCondition = availabilityCondition;
    myLibraryCategory = category;
  }

  @NotNull
  public FrameworkAvailabilityCondition getAvailabilityCondition() {
    return myAvailabilityCondition;
  }

  @NotNull
  @Override
  public String getDefaultLibraryName() {
    String libName = StringUtil.isEmptyOrSpaces(myLibraryName) ? myLibraryCategory : myLibraryName;
    return myVersionString.length() > 0 ? libName + "-" + myVersionString : myLibraryCategory;
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return getDefaultLibraryName();
  }

  @Override
  public String getVersionNumber() {
    return getVersionString();
  }
}
