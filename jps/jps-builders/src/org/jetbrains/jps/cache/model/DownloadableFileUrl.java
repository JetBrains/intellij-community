// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.cache.model;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.text.UniqueNameGenerator;
import org.jetbrains.annotations.NotNull;

public final class DownloadableFileUrl {
  private final String myFileName;
  private final String myFileExtension;
  private final String myDownloadUrl;

  public DownloadableFileUrl(@NotNull String downloadUrl, @NotNull String fileName) {
    String fileExtension = FileUtilRt.getExtension(fileName);
    myFileName = FileUtilRt.getNameWithoutExtension(fileName);
    myFileExtension = !fileExtension.isEmpty() && !fileExtension.startsWith(".") ? "." + fileExtension : fileExtension;
    myDownloadUrl = downloadUrl;
  }

  public @NotNull String getDownloadUrl() {
    return myDownloadUrl;
  }

  public @NotNull String getPresentableFileName() {
    return myFileName + myFileExtension;
  }

  public @NotNull String getPresentableDownloadUrl() {
    return myDownloadUrl;
  }

  public @NotNull String getDefaultFileName() {
    return generateFileName(Conditions.alwaysTrue());
  }

  public @NotNull String generateFileName(@NotNull Condition<? super String> validator) {
    return UniqueNameGenerator.generateUniqueName("", myFileName, myFileExtension, "_", "", validator);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    DownloadableFileUrl that = (DownloadableFileUrl)o;
    return myDownloadUrl.equals(that.myDownloadUrl);
  }

  @Override
  public int hashCode() {
    return myDownloadUrl.hashCode();
  }
}
