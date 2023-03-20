package org.jetbrains.jps.cache.model;

import java.io.File;

public class OutputLoadResult {
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
