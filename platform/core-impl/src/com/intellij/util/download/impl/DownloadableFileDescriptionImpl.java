// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.download.impl;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.util.download.DownloadableFileDescription;
import com.intellij.util.text.UniqueNameGenerator;
import org.jetbrains.annotations.NotNull;

public class DownloadableFileDescriptionImpl implements DownloadableFileDescription {
  private final String myFileName;
  private final String myFileExtension;
  private final String myDownloadUrl;

  public DownloadableFileDescriptionImpl(@NotNull String downloadUrl, @NotNull String fileName, @NotNull String fileExtension) {
    myFileName = fileName;
    myFileExtension = !fileExtension.isEmpty() && !fileExtension.startsWith(".") ? "." + fileExtension : fileExtension;
    myDownloadUrl = downloadUrl;
  }

  @Override
  public @NotNull String getDownloadUrl() {
    return myDownloadUrl;
  }

  @Override
  public @NotNull String getPresentableFileName() {
    return myFileName + myFileExtension;
  }

  @Override
  public @NotNull String getPresentableDownloadUrl() {
    return myDownloadUrl;
  }

  @Override
  public @NotNull String getDefaultFileName() {
    return generateFileName(Conditions.alwaysTrue());
  }

  @Override
  public @NotNull String generateFileName(@NotNull Condition<? super String> validator) {
    return UniqueNameGenerator.generateUniqueName("", myFileName, myFileExtension, "_", "", validator);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    DownloadableFileDescriptionImpl that = (DownloadableFileDescriptionImpl)o;
    return myDownloadUrl.equals(that.myDownloadUrl);
  }

  @Override
  public int hashCode() {
    return myDownloadUrl.hashCode();
  }
}
