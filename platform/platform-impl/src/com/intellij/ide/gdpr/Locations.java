/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.ide.gdpr;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.SystemInfo;

import java.io.File;

/**
 * @author Eugene Zhuravlev
 * Date: 06-Dec-17
 */
class Locations {
  private static final String RELATIVE_RESOURCE_PATH = "JetBrains";
  private static final File ourDataDir;

  static {
    File dataDir = null;
    if (SystemInfo.isWindows) {
      final String appdata = System.getenv("APPDATA");
      if (appdata != null) {
        dataDir = new File(appdata, RELATIVE_RESOURCE_PATH);
      }
    }
    else {
      final String userHome = System.getProperty("user.home");
      if (userHome != null) {
        if (SystemInfo.isMac) {
          final File dataRoot = new File(userHome, "/Library/Application Support");
          if (dataRoot.exists()) {
            dataDir = new File(dataRoot, RELATIVE_RESOURCE_PATH);
          }
        }
        else if (SystemInfo.isUnix) {
          final String dataHome = System.getenv("XDG_DATA_HOME");
          final File dataRoot = dataHome == null ? new File(userHome, ".local/share") : new File(dataHome);
          if (dataRoot.exists()) {
            dataDir = new File(dataRoot, RELATIVE_RESOURCE_PATH);
          }
        }
      }
    }
    if (dataDir == null)  {
      // default location
      dataDir = new File(PathManager.getSystemPath());
    }
    dataDir.mkdirs();
    ourDataDir = dataDir;
  }

  public static File getDataRoot() {
    return ourDataDir;
  }
}
