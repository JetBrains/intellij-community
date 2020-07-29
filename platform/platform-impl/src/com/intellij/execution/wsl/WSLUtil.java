// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.*;
import com.intellij.openapi.application.Experiments;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Class for working with WSL after Fall Creators Update
 * https://blogs.msdn.microsoft.com/commandline/2017/10/11/whats-new-in-wsl-in-windows-10-fall-creators-update/
 * - multiple linuxes
 * - file system is unavailable form windows (for now at least)
 */
public final class WSLUtil {
  public static final Logger LOG = Logger.getInstance("#com.intellij.execution.wsl");

  /**
   * this listener is a hack for https://github.com/Microsoft/BashOnWindows/issues/2592
   * See RUBY-20358
   */
  private static final ProcessListener INPUT_CLOSE_LISTENER = new ProcessAdapter() {
    @Override
    public void startNotified(@NotNull ProcessEvent event) {
      OutputStream input = event.getProcessHandler().getProcessInput();
      if (input != null) {
        try {
          input.flush();
          input.close();
        }
        catch (IOException ignore) {
        }
      }
    }
  };

  /**
   * @return true if there are distributions available for usage
   */
  public static boolean hasAvailableDistributions() {
    return !getAvailableDistributions().isEmpty();
  }


  /**
   * @return list of installed WSL distributions
   * @apiNote order of entries depends on configuration file and may change between launches.
   * @see WSLDistributionService
   */
  @NotNull
  public static List<WSLDistribution> getAvailableDistributions() {
    if (!isSystemCompatible()) return Collections.emptyList();

    final Path executableRoot = getExecutableRootPath();
    if (executableRoot == null) return Collections.emptyList();

    Collection<WslDistributionDescriptor> descriptors = WSLDistributionService.getInstance().getDescriptors();
    final List<WSLDistribution> result = new ArrayList<>(descriptors.size() + 1 /* LEGACY_WSL */);

    for (WslDistributionDescriptor descriptor: descriptors) {

      Path executablePath = Paths.get(descriptor.getExecutablePath());
      if (!executablePath.isAbsolute()) {
        executablePath = executableRoot.resolve(executablePath);
      }

      if (Files.exists(executablePath, LinkOption.NOFOLLOW_LINKS)) {
        result.add(new WSLDistribution(descriptor, executablePath));
      }
    }

    // add legacy WSL if it's available and enabled
    if (Experiments.getInstance().isFeatureEnabled("wsl.legacy.distribution")) {
      ContainerUtil.addIfNotNull(result, WSLDistributionLegacy.getInstance());
    }

    return result;
  }

  /**
   * @return root for WSL executable or null if unavailable
   */
  @Nullable
  private static Path getExecutableRootPath() {
    String localAppDataPath = System.getenv().get("LOCALAPPDATA");
    return StringUtil.isEmpty(localAppDataPath) ? null : Paths.get(localAppDataPath, "Microsoft\\WindowsApps");
  }

  /**
   * @return instance of WSL distribution or null if it's unavailable
   */
  @Nullable
  public static WSLDistribution getDistributionById(@Nullable String id) {
    if (id == null) {
      return null;
    }
    for (WSLDistribution distribution : getAvailableDistributions()) {
      if (id.equals(distribution.getId())) {
        return distribution;
      }
    }
    return null;
  }

  /**
   * @return instance of WSL distribution or null if it's unavailable
   */
  @Nullable
  public static WSLDistribution getDistributionByMsId(@Nullable String name) {
    if (name == null) {
      return null;
    }
    for (WSLDistribution distribution : getAvailableDistributions()) {
      if (name.equals(distribution.getMsId())) {
        return distribution;
      }
    }
    return null;
  }

  /**
   * Temporary hack method to fix <a href="https://github.com/Microsoft/BashOnWindows/issues/2592">WSL bug</a>
   * Must be invoked just before execution, see RUBY-20358
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.2")
  @NotNull
  public static <T extends ProcessHandler> T addInputCloseListener(@NotNull T processHandler) {
    if (Experiments.getInstance().isFeatureEnabled("wsl.close.process.input")) {
      processHandler.removeProcessListener(INPUT_CLOSE_LISTENER);
      processHandler.addProcessListener(INPUT_CLOSE_LISTENER);
    }
    return processHandler;
  }

  public static boolean isSystemCompatible() {
    return SystemInfo.isWin10OrNewer;
  }

  /**
   * @param wslPath a path in WSL file system, e.g. "/mnt/c/Users/file.txt" or "/c/Users/file.txt"
   * @param mntRoot a directory where fixed drives will be mounted. Default is "/mnt/" - {@link WSLDistribution#DEFAULT_WSL_MNT_ROOT}).
   *               See https://docs.microsoft.com/ru-ru/windows/wsl/wsl-config#configuration-options
   * @return Windows-dependent path to the file, pointed by {@code wslPath} in WSL or null if the path is unmappable.
   * For example, {@code getWindowsPath("/mnt/c/Users/file.txt", "/mnt/") returns "C:\Users\file.txt"}
   */
  @Nullable
  public static String getWindowsPath(@NotNull String wslPath, @NotNull String mntRoot) {
    if (!wslPath.startsWith(mntRoot)) {
      return null;
    }
    int driveLetterIndex = mntRoot.length();
    if (driveLetterIndex >= wslPath.length() || !Character.isLetter(wslPath.charAt(driveLetterIndex))) {
      return null;
    }
    int slashIndex = driveLetterIndex + 1;
    if (slashIndex < wslPath.length() && wslPath.charAt(slashIndex) != '/') {
      return null;
    }
    return FileUtil.toSystemDependentName(Character.toUpperCase(wslPath.charAt(driveLetterIndex)) + ":" + wslPath.substring(slashIndex));
  }

  /**
   * @return list of existing UNC roots for known WSL distributions
   */
  @ApiStatus.Experimental
  @NotNull
  public static List<File> getExistingUNCRoots() {
    if (!isSystemCompatible() || !Experiments.getInstance().isFeatureEnabled("wsl.p9.support")) {
      return Collections.emptyList();
    }
    return getAvailableDistributions().stream()
      .map(WSLDistribution::getUNCRoot)
      .filter(File::exists)
      .collect(Collectors.toList());
  }

  @NotNull
  public static ThreeState isWsl1(@NotNull WSLDistribution distribution) {
    try {
      ProcessOutput output = distribution.executeOnWsl(10_000, "uname", "-v");
      if (output.getExitCode() != 0) return ThreeState.UNSURE;
      return ThreeState.fromBoolean(output.getStdout().contains("Microsoft"));
    }
    catch (ExecutionException e) {
      LOG.warn(e);
      return ThreeState.UNSURE;
    }
  }
}
