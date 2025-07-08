// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileTypes;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeCoreBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.system.OS;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
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

    var commands = new ArrayList<String>();
    if (OS.CURRENT == OS.Windows) {
      //noinspection SpellCheckingInspection
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
      new GeneralCommandLine(commands).createProcess();
      return true;
    }
    catch (Exception e) {
      return false;
    }
  }
}
