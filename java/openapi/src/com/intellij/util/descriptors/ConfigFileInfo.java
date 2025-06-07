// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util.descriptors;

import org.jetbrains.annotations.NotNull;

public class ConfigFileInfo {
  private final @NotNull ConfigFileMetaData myMetaData;
  private final @NotNull String myUrl;


  public ConfigFileInfo(final @NotNull ConfigFileMetaData metaData, final @NotNull String url) {
    myMetaData = metaData;
    myUrl = url;
  }

  public @NotNull ConfigFileMetaData getMetaData() {
    return myMetaData;
  }

  public @NotNull String getUrl() {
    return myUrl;
  }


  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final ConfigFileInfo that = (ConfigFileInfo)o;

    if (!myMetaData.equals(that.myMetaData)) return false;
    if (!myUrl.equals(that.myUrl)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result;
    result = myMetaData.hashCode();
    result = 31 * result + myUrl.hashCode();
    return result;
  }
}
