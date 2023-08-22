// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.download.impl;

import com.intellij.util.download.DownloadableFileDescription;
import com.intellij.util.download.DownloadableFileSetDescription;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class DownloadableFileSetDescriptionImpl<F extends DownloadableFileDescription> implements DownloadableFileSetDescription {
  protected final List<? extends F> myFiles;
  protected final String myVersionString;
  private final String myName;

  public DownloadableFileSetDescriptionImpl(@NotNull String name,
                                            @NotNull String versionString,
                                            @NotNull List<? extends F> files) {
    myName = name;
    myVersionString = versionString;
    myFiles = files;
  }

  @Override
  public @NotNull String getName() {
    return myName;
  }

  @Override
  public @NotNull String getVersionString() {
    return myVersionString;
  }

  @Override
  public @NotNull List<F> getFiles() {
    return Collections.unmodifiableList(myFiles);
  }
}
