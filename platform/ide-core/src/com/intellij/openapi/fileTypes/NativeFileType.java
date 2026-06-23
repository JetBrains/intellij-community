// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileTypes;

import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeCoreBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.system.OS;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;
import java.awt.Desktop;
import java.util.ArrayList;

public final class NativeFileType implements INativeFileType {
  public static final NativeFileType INSTANCE = new NativeFileType();

  private NativeFileType() { }

  @Override
  public @NotNull String getName() {
    return "Native";
  }

  @Override
  public @NotNull String getDescription() {
    return IdeCoreBundle.message("filetype.native.description");
  }

  @Override
  public @Nls @NotNull String getDisplayName() {
    return IdeCoreBundle.message("filetype.native.display.name");
  }

  @Override
  public @NotNull String getDefaultExtension() {
    return "";
  }

  @Override
  public Icon getIcon() {
    return AllIcons.FileTypes.Custom;
  }

  @Override
  public boolean isBinary() {
    return true;
  }

  @Override
  public boolean openFileInAssociatedApplication(Project project, @NotNull VirtualFile file) {
    return openAssociatedApplication(file);
  }

  @Override
  public boolean useNativeIcon() {
    return true;
  }

  public static boolean openAssociatedApplication(@NotNull VirtualFile file) {
    if (!file.isInLocalFileSystem()) {
      throw new IllegalArgumentException("Non-local file: " + file + "; FS=" + file.getFileSystem());
    }

    if (Desktop.isDesktopSupported()) {
      var desktop = Desktop.getDesktop();
      if (desktop.isSupported(Desktop.Action.OPEN)) {
        try {
          //noinspection IO_FILE_USAGE
          desktop.open(file.toNioPath().toFile());
          return true;
        }
        catch (Exception e) {
          Logger.getInstance(NativeFileType.class).warn(e);
        }
      }
    }

    var commands = new ArrayList<String>();
    if (OS.CURRENT == OS.Windows) {
      commands.add("rundll32.exe");
      commands.add("url.dll,FileProtocolHandler");
    }
    else if (OS.CURRENT == OS.macOS) {
      commands.add("/usr/bin/open");
    }
    else if (PathEnvironmentVariableUtil.isOnPath("xdg-open")) {
      commands.add("xdg-open");
    }
    else {
      return false;
    }
    commands.add(file.getPresentableUrl());
    try {
      new ProcessBuilder(commands).start();
      return true;
    }
    catch (Exception e) {
      Logger.getInstance(NativeFileType.class).warn(e);
      return false;
    }
  }
}
