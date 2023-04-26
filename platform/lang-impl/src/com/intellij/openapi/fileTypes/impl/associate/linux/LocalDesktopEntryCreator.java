// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes.impl.associate.linux;

import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.fileTypes.impl.associate.OSFileAssociationException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public final class LocalDesktopEntryCreator {
  private static final String DESKTOP_ENTRY_PATH = ".local/share/applications";
  public static final String DESKTOP_ENTRY_PREFIX = "open-jb-";

  private LocalDesktopEntryCreator() {
  }

  static String createDesktopEntry() throws OSFileAssociationException {
    String entryFileName = getDesktopEntryFileName();
    StringBuilder builder = new StringBuilder();
    builder.append("[Desktop Entry]\n")
           .append("Type=Application\n")
           .append("Exec=").append(getLauncherPath()).append("\n");
    String path = getIconPath();
    if (path != null) {
      builder.append("Icon=").append(path).append("\n");
    }
    builder.append("Name=").append(ApplicationNamesInfo.getInstance().getFullProductName()).append("\n")
           .append("NoDisplay=true\n");
    try {
      String entryPath = System.getProperty("user.home") + File.separator + DESKTOP_ENTRY_PATH + File.separator + entryFileName;
      FileUtil.writeToFile(new File(entryPath), builder.toString());
    }
    catch (IOException e) {
      throw new OSFileAssociationException("Desktop entry file " + entryFileName + " can't be created: " + e.getMessage());
    }
    return entryFileName;
  }

  @Nullable
  private static String getIconPath() {
    final String launcherPath = getLauncherPath();
    if (launcherPath.startsWith("/") && launcherPath.endsWith(".sh")) {
      return StringUtil.trimEnd(launcherPath, ".sh");
    }
    return null;
  }

  private static String getDesktopEntryFileName() {
    return DESKTOP_ENTRY_PREFIX + ApplicationNamesInfo.getInstance().getDefaultLauncherName() + ".desktop";
  }

  private static String getLauncherPath() {
    final String scriptName = ApplicationNamesInfo.getInstance().getScriptName();
    Path scriptPath = PathManager.findBinFile(scriptName + ".sh");
    return scriptPath != null ? scriptPath.toString() : ApplicationNamesInfo.getInstance().getDefaultLauncherName();
  }
}
