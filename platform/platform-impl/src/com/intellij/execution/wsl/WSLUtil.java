// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl;

import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Class for working with WSL after Fall Creators Update
 * https://blogs.msdn.microsoft.com/commandline/2017/10/11/whats-new-in-wsl-in-windows-10-fall-creators-update/
 * - multiple linuxes
 * - file system is unavailable form windows (for now at least)
 */
public class WSLUtil {
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
   * @return
   */
  public static boolean hasAvailableDistributions() {
    return getAvailableDistributions().size() > 0;
  }


  /**
   * @return list of installed WSL distributions
   */
  @NotNull
  public static List<WSLDistribution> getAvailableDistributions() {
    if (!isSystemCompatible()) return Collections.emptyList();

    final Path executableRoot = getExecutableRootPath();
    if (executableRoot == null) return Collections.emptyList();

    List<WslDistributionDescriptor> descriptors = WSLDistributionService.getInstance().getDescriptors();
    final List<WSLDistribution> result = new ArrayList<>(descriptors.size() + 1 /* LEGACY_WSL */);

    for (WslDistributionDescriptor descriptor: descriptors) {
      final Path executablePath = executableRoot.resolve(descriptor.getExeName());
      if (Files.exists(executablePath, LinkOption.NOFOLLOW_LINKS)) {
        result.add(new WSLDistribution(descriptor, executablePath));
      }
    }
    // add legacy WSL if it's available
    ContainerUtil.addIfNotNull(result, WSLDistributionLegacy.getInstance());

    return Collections.unmodifiableList(result);
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
   * Temporary hack method to fix <a href="https://github.com/Microsoft/BashOnWindows/issues/2592">WSL bug</a>
   * Must be invoked just before execution, see RUBY-20358
   */
  @NotNull
  public static <T extends ProcessHandler> T addInputCloseListener(@NotNull T processHandler) {
    processHandler.removeProcessListener(INPUT_CLOSE_LISTENER);
    processHandler.addProcessListener(INPUT_CLOSE_LISTENER);
    return processHandler;
  }

  public static boolean isSystemCompatible() {
    return SystemInfo.isWin10OrNewer;
  }

  /**
   * @return Windows-dependent path for a file, pointed by {@code wslPath} in WSL or null if path is unmappable.
   *         For example, {@code getWindowsPath("/mnt/c/Users/file.txt") returns "C:\Users\file.txt"}
   */
  @Nullable
  public static String getWindowsPath(@NotNull String wslPath) {
    if (!wslPath.startsWith(WSLDistribution.WSL_MNT_ROOT) || wslPath.length() <= WSLDistribution.WSL_MNT_ROOT.length()) {
      return null;
    }
    int driveIndex = WSLDistribution.WSL_MNT_ROOT.length();
    if (!Character.isLetter(wslPath.charAt(driveIndex))) {
      return null;
    }
    int slashIndex = driveIndex + 1;
    if (slashIndex < wslPath.length() && wslPath.charAt(slashIndex) != '/') {
      return null;
    }
    return FileUtil.toSystemDependentName(wslPath.charAt(driveIndex) + ":" + wslPath.substring(slashIndex));
  }
}
