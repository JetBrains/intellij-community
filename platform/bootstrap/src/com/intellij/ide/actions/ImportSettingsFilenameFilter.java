// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.Set;
import java.util.function.Predicate;

/**
 * This class is serialized into StartupActionScript stream and must thus reside in bootstrap module.
 */
public final class ImportSettingsFilenameFilter implements Predicate<String>, Serializable {
  public static final @NonNls String SETTINGS_JAR_MARKER = "IntelliJ IDEA Global Settings";

  private static final long serialVersionUID = 201708031943L;

  private final String[] myRelativeNamesToExtract;

  public ImportSettingsFilenameFilter(@NotNull Set<String> relativeNamesToExtract) {
    //noinspection SSBasedInspection
    myRelativeNamesToExtract = relativeNamesToExtract.toArray(new String[0]);
  }

  @Override
  public boolean test(@NotNull String relativePath) {
    if (relativePath.equals(SETTINGS_JAR_MARKER)) {
      return false;
    }

    relativePath = relativePath.replace('\\', '/');
    for (String allowedRelPath : myRelativeNamesToExtract) {
      if (relativePath.startsWith(allowedRelPath)) {
        return true;
      }
    }
    return false;
  }
}