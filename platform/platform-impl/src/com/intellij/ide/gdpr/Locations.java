// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.gdpr;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfoRt;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author Eugene Zhuravlev
 */
public final class Locations {
  private static final Logger LOG = Logger.getInstance(Locations.class);
  private static final Path ourDataDir;

  static {
    String relativeResourcePath = getRelativeResourcePath();

    Path dataDir = null;
    if (SystemInfoRt.isWindows) {
      String appdata = System.getenv("APPDATA");
      if (appdata != null) {
        dataDir = Paths.get(appdata, relativeResourcePath);
      }
    }
    else {
      String userHome = System.getProperty("user.home");
      if (userHome != null) {
        Path userHomeDir = Paths.get(userHome);
        if (SystemInfoRt.isMac) {
          Path dataRoot = userHomeDir.resolve("Library/Application Support");
          if (Files.isDirectory(dataRoot)) {
            dataDir = dataRoot.resolve(relativeResourcePath);
          }
        }
        else if (SystemInfoRt.isUnix) {
          String dataHome = System.getenv("XDG_DATA_HOME");
          Path dataRoot = dataHome == null ? userHomeDir.resolve(".local/share") : Paths.get(dataHome);
          if (Files.exists(dataRoot)) {
            dataDir = dataRoot.resolve(relativeResourcePath);
          }
        }
      }
    }
    if (dataDir == null)  {
      // default location
      dataDir = Paths.get(PathManager.getSystemPath());
    }
    try {
      Files.createDirectories(dataDir);
    }
    catch (IOException e) {
      LOG.error(e);
    }
    ourDataDir = dataDir;
  }

  public static Path getDataRoot() {
    return ourDataDir;
  }

  private static @NotNull String getRelativeResourcePath() {
    try {
      ApplicationInfoEx appInfo = ApplicationInfoImpl.getShadowInstance();
      String path = appInfo.getShortCompanyName();
      return appInfo.isVendorJetBrains() ? "JetBrains" : path == null ? "unknown_vendor" : path.trim().replace(' ', '_');
    }
    catch (Throwable e) {
      LOG.info("Problems initializing location path",  e);
      // default vendor
      return "JetBrains";
    }
  }
}
