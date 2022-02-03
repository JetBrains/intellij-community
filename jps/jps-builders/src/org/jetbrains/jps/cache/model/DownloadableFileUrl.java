// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.cache.model;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.text.UniqueNameGenerator;
import org.jetbrains.annotations.NotNull;

public class DownloadableFileUrl {
  private final String myFileName;
  private final String myFileExtension;
  private final String myDownloadUrl;

  public DownloadableFileUrl(@NotNull String downloadUrl, @NotNull String fileName) {
    String fileExtension = FileUtilRt.getExtension(fileName);
    myFileName = FileUtilRt.getNameWithoutExtension(fileName);
    myFileExtension = fileExtension.length() > 0 && !fileExtension.startsWith(".") ? "." + fileExtension : fileExtension;
    myDownloadUrl = downloadUrl;
  }

  @NotNull
  public String getDownloadUrl() {
    return myDownloadUrl;
  }

  @NotNull
  public String getPresentableFileName() {
    return myFileName + myFileExtension;
  }

  @NotNull
  public String getPresentableDownloadUrl() {
    return myDownloadUrl;
  }

  @NotNull
  public String getDefaultFileName() {
    return generateFileName(Conditions.alwaysTrue());
  }

  @NotNull
  public String generateFileName(@NotNull Condition<? super String> validator) {
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
