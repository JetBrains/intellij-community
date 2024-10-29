// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.impl.wsl.WslConstants;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import static com.intellij.openapi.util.NullableLazyValue.lazyNullable;

/**
 * A tool for working with WSL distributions via the `wsl.exe` utility refined in
 * <a href="https://blogs.msdn.microsoft.com/commandline/2017/10/11/whats-new-in-wsl-in-windows-10-fall-creators-update/">Windows 10 1709</a>.
 */
public final class WSLUtil {
  public static final Logger LOG = Logger.getInstance("#com.intellij.execution.wsl");

  /**
   * @deprecated use {@link WslDistributionManager#getInstalledDistributions()} instead.
   * Method will be removed after we check statistics and make sure versions before 1903 aren't used.
   */
  @Deprecated(forRemoval = true)
  @SuppressWarnings("DeprecatedIsStillUsed")
  public static @NotNull List<WSLDistribution> getAvailableDistributions() {
    if (!isSystemCompatible()) return Collections.emptyList();

    final Path executableRoot = getExecutableRootPath();
    if (executableRoot == null) return Collections.emptyList();

    Collection<WslDistributionDescriptor> descriptors = WSLDistributionService.getInstance().getDescriptors();
    final List<WSLDistribution> result = new ArrayList<>(descriptors.size() + 1 /* LEGACY_WSL */);

    for (WslDistributionDescriptor descriptor : descriptors) {
      String executablePathStr = descriptor.getExecutablePath();
      if (executablePathStr != null) {
        Path executablePath = Paths.get(executablePathStr);
        if (!executablePath.isAbsolute()) {
          executablePath = executableRoot.resolve(executablePath);
        }

        if (Files.exists(executablePath, LinkOption.NOFOLLOW_LINKS)) {
          result.add(new WSLDistribution(descriptor, executablePath));
        }
      }
    }

    return result;
  }

  /**
   * @return root for WSL executable or null if unavailable
   */
  private static @Nullable Path getExecutableRootPath() {
    String localAppDataPath = System.getenv().get("LOCALAPPDATA");
    return StringUtil.isEmpty(localAppDataPath) ? null : Paths.get(localAppDataPath, "Microsoft\\WindowsApps");
  }

  private static boolean ourIsSystemCompatible = SystemInfo.isWin10OrNewer;

  public static boolean isSystemCompatible() {
    return ourIsSystemCompatible;
  }

  /**
   * On the first side, functions like [parseWindowsUncPath] are not supposed to return a Windows path on Unix. Otherwise, API users
   * can make false assumptions. On the other hand, such limitation hinders creation of OS-agnostic unit tests for this code.
   */
  @TestOnly
  public static void setSystemCompatible(boolean value) {
    ourIsSystemCompatible = value;
  }

  /**
   * @param wslPath a path in WSL file system, e.g. "/mnt/c/Users/file.txt" or "/c/Users/file.txt"
   * @param mntRoot a directory where fixed drives will be mounted. Default is "/mnt/" - {@link WSLDistribution#DEFAULT_WSL_MNT_ROOT}).
   *                See <a href="https://docs.microsoft.com/ru-ru/windows/wsl/wsl-config#configuration-options">WSL configuration options</a>.
   * @return Windows-dependent path to the file, pointed by {@code wslPath} in WSL or null if the path is unmappable.
   * For example, {@code getWindowsPath("/mnt/c/Users/file.txt", "/mnt/") returns "C:\Users\file.txt"}
   */
  public static @Nullable String getWindowsPath(@NotNull String wslPath, @NotNull String mntRoot) {
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
   * @return version if it can be determined or -1 instead
   */
  static int getWslVersion(@NotNull WSLDistribution distribution) {
    int version = getVersionFromWslCli(distribution);
    if (version < 0) {
      version = getVersionByUname(distribution);
    }
    return version;
  }

  private static int getVersionFromWslCli(@NotNull WSLDistribution distribution) {
    try {
      final List<WslDistributionAndVersion> versions = WslDistributionManager.getInstance().loadInstalledDistributionsWithVersions();
      final WslDistributionAndVersion distributionAndVersion =
        ContainerUtil.find(versions, version -> version.getDistributionName().equals(distribution.getMsId()));
      if (distributionAndVersion != null) {
        return distributionAndVersion.getVersion();
      }
      LOG.warn("WSL distribution '" + distribution.getMsId() + "' not found");
    }
    catch (IOException | IllegalStateException e) {
      LOG.warn("Failed to calculate version for " + distribution.getMsId() + ": " + e.getMessage());
    }
    return -1;
  }

  // To be removed when old WSL installations (without wsl.exe) are gone.
  private static int getVersionByUname(@NotNull WSLDistribution distribution) {
    try {
      ProcessOutput output = distribution.executeOnWsl(WSLDistribution.DEFAULT_TIMEOUT, "uname", "-v");
      if (output.checkSuccess(LOG)) {
        return output.getStdout().contains("Microsoft") ? 1 : 2;
      }
    }
    catch (ExecutionException e) {
      LOG.warn(e);
    }
    return -1;
  }

  public static @NotNull @NlsSafe String getMsId(@NotNull @NlsSafe String msOrInternalId) {
    WslDistributionDescriptor descriptor = ContainerUtil.find(WSLDistributionService.getInstance().getDescriptors(),
                                                              d -> d.getId().equals(msOrInternalId));
    return descriptor != null ? descriptor.getMsId() : msOrInternalId;
  }


  static final class WSLToolFlags {
    public final boolean isQuietFlagAvailable;
    public final boolean isVerboseFlagAvailable;

    WSLToolFlags(boolean isQuietAvailable, boolean isVerboseAvailable) {
      isQuietFlagAvailable = isQuietAvailable;
      isVerboseFlagAvailable = isVerboseAvailable;
    }
  }

  private static final NullableLazyValue<WSLToolFlags> WSL_TOOL_FLAGS = lazyNullable(() -> getWSLToolFlagsInternal());

  static @Nullable WSLToolFlags getWSLToolFlags() {
    return WSL_TOOL_FLAGS.getValue();
  }

  private static final Pattern QUIET = Pattern.compile("\\s--quiet,?\\b");
  private static final Pattern VERBOSE = Pattern.compile("\\s--verbose,?\\b");

  private static @Nullable WSLToolFlags getWSLToolFlagsInternal() {
    final Path wslExe = WSLDistribution.findWslExe();
    if (wslExe == null) return null;

    final GeneralCommandLine commandLine = new GeneralCommandLine(wslExe.toString(), "--help").
      withCharset(StandardCharsets.UTF_16LE);

    try {
      final ProcessOutput output = ExecUtil.execAndGetOutput(commandLine, 5000);
      if (output.isTimeout()) return null;

      // intentionally do no check "wsl --help" exit code because it returns -1
      final String stdout = output.getStdout();
      return new WSLToolFlags(QUIET.matcher(stdout).find(),
                              VERBOSE.matcher(stdout).find());
    }
    catch (Exception e) {
      LOG.warn(e);
      return null;
    }
  }

  static @NotNull String getUncPrefix() {
    return SystemInfo.isWin11OrNewer ? "\\\\wsl.localhost\\" : WslConstants.UNC_PREFIX;
  }
}
