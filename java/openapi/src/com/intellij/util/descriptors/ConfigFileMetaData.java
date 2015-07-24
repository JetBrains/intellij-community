/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.util.descriptors;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.diagnostic.Logger;

import java.util.Arrays;

/**
 * @author nik
 */
public class ConfigFileMetaData {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.descriptors.ConfigFileMetaData");
  private final String myTitle;
  private final String myId;
  private final String myFileName;
  private final String myDirectoryPath;
  private final ConfigFileVersion[] myVersions;
  private final ConfigFileVersion myDefaultVersion;
  private final boolean myOptional;
  private final boolean myFileNameFixed;
  private final boolean myUnique;

  public ConfigFileMetaData(final String title,
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
    myVersions = versions;
    myOptional = optional;
    myDefaultVersion = defaultVersion != null ? defaultVersion : myVersions[myVersions.length - 1];
    LOG.assertTrue(Arrays.asList(myVersions).contains(myDefaultVersion));
  }

  public ConfigFileMetaData(final String title,
                            final @NonNls String fileName,
                            final @NonNls String directoryPath,
                            final ConfigFileVersion[] versions,
                            ConfigFileVersion defaultVersion,
                            boolean optional,
                            final boolean fileNameFixed,
                            final boolean unique) {
    this(title, fileName, fileName, directoryPath, versions, defaultVersion, optional, fileNameFixed, unique);
  }

  public String getTitle() {
    return myTitle;
  }

  public String getId() {
    return myId;
  }

  public String getFileName() {
    return myFileName;
  }

  public String getDirectoryPath() {
    return myDirectoryPath;
  }

  public boolean isOptional() {
    return myOptional;
  }

  public ConfigFileVersion[] getVersions() {
    return myVersions;
  }


  public String toString() {
    return myTitle;
  }

  public ConfigFileVersion getDefaultVersion() {
    return myDefaultVersion;
  }

  public boolean isFileNameFixed() {
    return myFileNameFixed;
  }

  public boolean isUnique() {
    return myUnique;
  }
}
