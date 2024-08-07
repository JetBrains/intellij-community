// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.descriptors;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public final class ConfigFileMetaData {
  private static final Logger LOG = Logger.getInstance(ConfigFileMetaData.class);
  @Nls private final String myTitle;
  private final String myId;
  private final String myFileName;
  private final String myDirectoryPath;
  private final ConfigFileVersion[] versions;
  private final ConfigFileVersion defaultVersion;
  private final boolean isOptional;
  private final boolean myFileNameFixed;
  private final boolean myUnique;

  public ConfigFileMetaData(@Nls final String title,
                            final @NonNls String id,
                            final @NonNls String fileName,
                            final @NonNls String directoryPath,
                            final ConfigFileVersion[] versions,
                            final @Nullable ConfigFileVersion defaultVersion,
                            final boolean optional,
                            final boolean fileNameFixed,
                            final boolean unique) {
    myTitle = title;
    myId = id;
    myFileName = fileName;
    myDirectoryPath = directoryPath;
    myFileNameFixed = fileNameFixed;
    myUnique = unique;
    LOG.assertTrue(versions.length > 0, "No versions specified for '" + id + "' descriptor");
    this.versions = versions;
    isOptional = optional;
    this.defaultVersion = defaultVersion == null ? versions[versions.length - 1] : defaultVersion;
    LOG.assertTrue(Arrays.asList(versions).contains(this.defaultVersion));
  }

  public ConfigFileMetaData(final @Nls String title,
                            final @NonNls String fileName,
                            final @NonNls String directoryPath,
                            final ConfigFileVersion[] versions,
                            ConfigFileVersion defaultVersion,
                            boolean optional,
                            final boolean fileNameFixed,
                            final boolean unique) {
    this(title, fileName, fileName, directoryPath, versions, defaultVersion, optional, fileNameFixed, unique);
  }

  @Nls
  public String getTitle() {
    return myTitle;
  }

  public String getId() {
    return myId;
  }

  @NlsSafe
  public String getFileName() {
    return myFileName;
  }

  public String getDirectoryPath() {
    return myDirectoryPath;
  }

  public boolean isOptional() {
    return isOptional;
  }

  public ConfigFileVersion[] getVersions() {
    return versions;
  }

  public String toString() {
    return myTitle;
  }

  public ConfigFileVersion getDefaultVersion() {
    return defaultVersion;
  }

  public boolean isFileNameFixed() {
    return myFileNameFixed;
  }

  public boolean isUnique() {
    return myUnique;
  }
}
