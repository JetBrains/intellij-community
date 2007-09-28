/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ide.actions;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.FilenameFilter;
import java.io.Serializable;
import java.util.Set;

/**
 * @author mike
*/
public class ImportSettingsFilenameFilter implements FilenameFilter, Serializable {
  private final Set<String> myRelativeNamesToExtract;
  @NonNls static final String SETTINGS_JAR_MARKER = "IntelliJ IDEA Global Settings";

  public ImportSettingsFilenameFilter(Set<String> relativeNamesToExtract) {
    myRelativeNamesToExtract = relativeNamesToExtract;
  }

  public boolean accept(File dir, String name) {
    if (name.equals(SETTINGS_JAR_MARKER)) return false;
    final File configPath = new File(PathManager.getConfigPath());
    final String rPath = FileUtil.getRelativePath(configPath, new File(dir, name));
    assert rPath != null;
    final String relativePath = FileUtil.toSystemIndependentName(rPath);
    for (final String allowedRelPath : myRelativeNamesToExtract) {
      if (relativePath.startsWith(allowedRelPath)) return true;
    }
    return false;
  }
}
