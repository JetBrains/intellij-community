package org.jetbrains.jps.cache.model;

import java.io.File;

public class OutputLoadResult {
  private File zipFile;
  private String downloadUrl;
  private AffectedModule module;

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
