// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit.actions.associate.linux;

import com.intellij.ide.lightEdit.actions.associate.FileAssociationException;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public final class LocalDesktopEntryCreator {
  private static final String DESKTOP_ENTRY_PATH = ".local/share/applications";

  private LocalDesktopEntryCreator() {
  }

  static String createDesktopEntry() throws FileAssociationException {
    String entryFileName = getDesktopEntryFileName();
    StringBuilder builder = new StringBuilder();
    builder.append("[Desktop Entry]\n")
           .append("Type=Application\n")
           .append("Exec=").append(getLauncherPath()).append("\n");
    ObjectUtils.consumeIfNotNull(
      getIconPath(),
      iconPath -> builder.append("Icon=").append(iconPath).append("\n")
    );
    builder.append("Name=").append(ApplicationNamesInfo.getInstance().getFullProductName()).append("\n")
           .append("NoDisplay=true\n");
    try {
      String entryPath = System.getProperty("user.home") + File.separator + DESKTOP_ENTRY_PATH + File.separator + entryFileName;
      FileUtil.writeToFile(new File(entryPath), builder.toString());
    }
    catch (IOException e) {
      throw new FileAssociationException("Desktop entry file " + entryFileName + " can't be created: " + e.getMessage());
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
    return ApplicationNamesInfo.getInstance().getDefaultLauncherName() + ".desktop";
  }

  private static String getLauncherPath() {
    final String scriptName = ApplicationNamesInfo.getInstance().getScriptName();
    Path scriptPath = PathManager.findBinFile(scriptName + ".sh");
    return scriptPath != null ? scriptPath.toString() : ApplicationNamesInfo.getInstance().getDefaultLauncherName();
  }
}
