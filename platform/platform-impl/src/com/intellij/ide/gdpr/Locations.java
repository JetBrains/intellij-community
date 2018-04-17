/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.ide.gdpr;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author Eugene Zhuravlev
 * Date: 06-Dec-17
 */
class Locations {
  private static final File ourDataDir;

  static {
    final ApplicationInfoEx appInfo = ApplicationInfoImpl.getShadowInstance();
    final String relativeResourcePath = appInfo.isVendorJetBrains() ? "JetBrains" : normalizePathName(appInfo.getShortCompanyName());

    File dataDir = null;
    if (SystemInfo.isWindows) {
      final String appdata = System.getenv("APPDATA");
      if (appdata != null) {
        dataDir = new File(appdata, relativeResourcePath);
      }
    }
    else {
      final String userHome = System.getProperty("user.home");
      if (userHome != null) {
        if (SystemInfo.isMac) {
          final File dataRoot = new File(userHome, "/Library/Application Support");
          if (dataRoot.exists()) {
            dataDir = new File(dataRoot, relativeResourcePath);
          }
        }
        else if (SystemInfo.isUnix) {
          final String dataHome = System.getenv("XDG_DATA_HOME");
          final File dataRoot = dataHome == null ? new File(userHome, ".local/share") : new File(dataHome);
          if (dataRoot.exists()) {
            dataDir = new File(dataRoot, relativeResourcePath);
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


  @NotNull
  private static String normalizePathName(String path) {
    return path == null? "unknown_vendor" : path.trim().replace(' ', '_');
  }
}
