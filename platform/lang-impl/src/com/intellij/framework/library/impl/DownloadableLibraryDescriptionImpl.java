// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.framework.library.impl;

import com.intellij.framework.library.DownloadableLibraryDescription;
import com.intellij.framework.library.FrameworkLibraryVersion;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class DownloadableLibraryDescriptionImpl implements DownloadableLibraryDescription {
  private final List<FrameworkLibraryVersion> myVersions;

  public DownloadableLibraryDescriptionImpl(List<FrameworkLibraryVersion> versions) {
    myVersions = versions;
  }

  public List<? extends FrameworkLibraryVersion> getVersions() {
    return myVersions;
  }

  @Override
  public void fetchVersions(@NotNull FileSetVersionsCallback<FrameworkLibraryVersion> callback) {
    callback.onSuccess(myVersions);
  }

  @Override
  public @NotNull List<FrameworkLibraryVersion> fetchVersions() {
    return myVersions;
  }
}
