// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util.descriptors;

import org.jetbrains.annotations.NotNull;

public final class ConfigFileUtil {
  private ConfigFileUtil() {
  }

  public static @NotNull ConfigFileVersion getVersionByName(ConfigFileMetaData metaData, String name) {
    for (ConfigFileVersion version : metaData.getVersions()) {
      if (name.equals(version.getName())) {
        return version;
      }
    }
    return metaData.getDefaultVersion();
  }

}
