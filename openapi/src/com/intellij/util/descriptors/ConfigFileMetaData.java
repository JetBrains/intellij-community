/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
 *
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
  private String myTitle;
  private String myId;
  private String myFileName;
  private String myDirectoryPath;
  private ConfigFileVersion[] myVersions;
  private ConfigFileVersion myDefaultVersion;
  private boolean myOptional;

  public ConfigFileMetaData(final String title, final @NonNls String id, final @NonNls String fileName, final @NonNls String directoryPath,
                                    final ConfigFileVersion[] versions, @Nullable ConfigFileVersion defaultVersion, boolean optional) {
    myTitle = title;
    myId = id;
    myFileName = fileName;
    myDirectoryPath = directoryPath;
    LOG.assertTrue(versions.length > 0);
    myVersions = versions;
    myOptional = optional;
    myDefaultVersion = defaultVersion != null ? defaultVersion : myVersions[myVersions.length - 1];
    LOG.assertTrue(Arrays.asList(myVersions).contains(myDefaultVersion));
  }

  public ConfigFileMetaData(final String title, final @NonNls String id, final @NonNls String fileName, final @NonNls String directoryPath,
                                    final ConfigFileVersion[] versions) {
    this(title, id, fileName, directoryPath, versions, null, false);
  }

  public ConfigFileMetaData(final String title, final @NonNls String fileName, final @NonNls String directoryPath,
                                    final ConfigFileVersion[] versions, boolean optional) {
    this(title, fileName, fileName, directoryPath, versions, null, optional);
  }

  public ConfigFileMetaData(final String title, final @NonNls String fileName, final @NonNls String directoryPath,
                                    final ConfigFileVersion[] versions, ConfigFileVersion defaultVersion, boolean optional) {
    this(title, fileName, fileName, directoryPath, versions, defaultVersion, optional);
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
}
