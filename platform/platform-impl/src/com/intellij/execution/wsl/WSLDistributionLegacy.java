// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl;

import com.intellij.openapi.util.AtomicNullableLazyValue;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Represents legacy bash.exe WSL, see RUBY-20359
 */
public class WSLDistributionLegacy extends WSLDistribution {
  public static final WSLDistributionLegacy LEGACY_WSL = new WSLDistributionLegacy();

  private static final String WSL_ROOT_CHUNK = "\\lxss\\rootfs";

  private static final AtomicNullableLazyValue<String> WSL_ROOT_IN_WINDOWS_PROVIDER = AtomicNullableLazyValue.createValue(() -> {
    String localAppDataPath = System.getenv().get("LOCALAPPDATA");
    return StringUtil.isEmpty(localAppDataPath) ? null : localAppDataPath + WSL_ROOT_CHUNK;
  });

  private WSLDistributionLegacy() {
    super("UBUNTU_LEGACY", "ubuntu_bash", "bash.exe", "Ubuntu (Legacy)");
  }

  @Nullable
  @Override
  protected Path getExecutableRootPath() {
    String windir = System.getenv().get("windir");
    return StringUtil.isEmpty(windir) ? null : Paths.get(windir, "System32");
  }

  @NotNull
  @Override
  protected String getRunCommandLineParameter() {
    return "-c";
  }

  @Nullable
  @Override
  public String getWslPath(@NotNull String windowsPath) {
    String wslRootInHost = WSL_ROOT_IN_WINDOWS_PROVIDER.getValue();
    if (wslRootInHost == null) {
      return null;
    }

    if (FileUtil.isAncestor(wslRootInHost, windowsPath, true)) {  // this is some internal WSL file
      return FileUtil.toSystemIndependentName(windowsPath.substring(wslRootInHost.length()));
    }

    return super.getWslPath(windowsPath);
  }

  @Nullable
  @Override
  public String getWindowsPath(@NotNull String wslPath) {
    String windowsPath = super.getWindowsPath(wslPath);
    if (windowsPath != null) {
      return windowsPath;
    }

    String wslRootInHost = WSL_ROOT_IN_WINDOWS_PROVIDER.getValue();
    if (wslRootInHost == null) {
      return null;
    }
    return FileUtil.toSystemDependentName(wslRootInHost + wslPath);
  }
}
