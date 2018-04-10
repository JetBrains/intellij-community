// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl;

import com.intellij.openapi.util.AtomicNullableLazyValue;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Represents legacy bash.exe WSL, see RUBY-20359
 */
public class WSLDistributionLegacy extends WSLDistribution {
  private static final WSLDistribution.Description LEGACY_WSL =
    new WSLDistribution.Description("UBUNTU_LEGACY", "ubuntu_bash", "bash.exe", "Ubuntu (Legacy)");

  private static final String WSL_ROOT_CHUNK = "\\lxss\\rootfs";

  private static final AtomicNullableLazyValue<String> WSL_ROOT_IN_WINDOWS_PROVIDER = AtomicNullableLazyValue.createValue(() -> {
    String localAppDataPath = System.getenv().get("LOCALAPPDATA");
    return StringUtil.isEmpty(localAppDataPath) ? null : localAppDataPath + WSL_ROOT_CHUNK;
  });

  @Nullable
  private static Path getExecutableRootPath() {
    String windir = System.getenv().get("windir");
    return StringUtil.isEmpty(windir) ? null : Paths.get(windir, "System32");
  }

  /**
   * @return legacy WSL ("Bash-on-Windows") if it's available, <code>null</code> otherwise
   */
  @Nullable
  static WSLDistribution getInstance() {
    final Path executableRoot = getExecutableRootPath();
    if (executableRoot == null) return null;

    final Path executablePath = executableRoot.resolve(LEGACY_WSL.exeName);
    if (Files.exists(executablePath, LinkOption.NOFOLLOW_LINKS)) {
      return new WSLDistributionLegacy(executablePath);
    }
    return null;
  }

  private WSLDistributionLegacy(@NotNull Path executablePath) {
    super(LEGACY_WSL, executablePath);
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
