// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FilenameFilter;
import java.io.Serializable;
import java.util.Set;

/**
 * This class is serialized into StartupActionScript stream and must thus reside in bootstrap module.
 *
 * @author mike
 */
public class ImportSettingsFilenameFilter implements FilenameFilter, Serializable {
  public static final String SETTINGS_JAR_MARKER = "IntelliJ IDEA Global Settings";

  private static final long serialVersionUID = 201708031943L;

  private final String[] myRelativeNamesToExtract;

  public ImportSettingsFilenameFilter(@NotNull Set<String> relativeNamesToExtract) {
    myRelativeNamesToExtract = ArrayUtilRt.toStringArray(relativeNamesToExtract);
  }

  @Override
  public boolean accept(File dir, String name) {
    if (name.equals(SETTINGS_JAR_MARKER)) return false;

    File configPath = new File(PathManager.getConfigPath());
    String rPath = FileUtil.getRelativePath(configPath, new File(dir, name));
    assert rPath != null;
    String relativePath = FileUtil.toSystemIndependentName(rPath);
    for (String allowedRelPath : myRelativeNamesToExtract) {
      if (relativePath.startsWith(allowedRelPath)) {
        return true;
      }
    }

    return false;
  }
}