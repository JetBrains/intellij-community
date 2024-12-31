// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.include;

import org.jetbrains.annotations.NotNull;

import java.io.File;

final class FileIncludeInfoImpl extends FileIncludeInfo {

  public final String providerId;

  FileIncludeInfoImpl(@NotNull String path, int offset, boolean runtimeOnly, String providerId) {
    super(new File(path).getName(), path, offset, runtimeOnly);
    this.providerId = providerId;
  }

  @SuppressWarnings({"RedundantIfStatement"})
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    FileIncludeInfoImpl that = (FileIncludeInfoImpl)o;

    if (!fileName.equals(that.fileName)) return false;
    if (!path.equals(that.path)) return false;
    if (offset != that.offset) return false;
    if (runtimeOnly != that.runtimeOnly) return false;
    if (!providerId.equals(that.providerId)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = fileName.hashCode();
    result = 31 * result + path.hashCode();
    result = 31 * result + offset;
    result = 31 * result + (runtimeOnly ? 1 : 0);
    return result;
  }

  @Override
  public String toString() {
    return "FileIncludeInfoImpl{" +
           "fileName='" + fileName + '\'' +
           ", path='" + path + '\'' +
           ", offset=" + offset +
           ", runtimeOnly=" + runtimeOnly +
           ", providerId='" + providerId + '\'' +
           '}';
  }
}
