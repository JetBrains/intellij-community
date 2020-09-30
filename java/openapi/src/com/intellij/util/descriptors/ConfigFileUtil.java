// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util.descriptors;

import org.jetbrains.annotations.NotNull;

public final class ConfigFileUtil {
  private ConfigFileUtil() {
  }

  @NotNull
  public static ConfigFileVersion getVersionByName(ConfigFileMetaData metaData, String name) {
    for (ConfigFileVersion version : metaData.getVersions()) {
      if (name.equals(version.getName())) {
        return version;
      }
    }
    return metaData.getDefaultVersion();
  }

}
