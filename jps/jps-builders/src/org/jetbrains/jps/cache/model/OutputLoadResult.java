// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.cache.model;

import java.io.File;

public final class OutputLoadResult {
  private final File zipFile;
  private final String downloadUrl;
  private final AffectedModule module;

  public OutputLoadResult(File zipFile, String downloadUrl, AffectedModule module) {
    this.zipFile = zipFile;
    this.downloadUrl = downloadUrl;
    this.module = module;
  }

  public File getZipFile() {
    return zipFile;
  }

  public String getDownloadUrl() {
    return downloadUrl;
  }

  public AffectedModule getModule() {
    return module;
  }
}
