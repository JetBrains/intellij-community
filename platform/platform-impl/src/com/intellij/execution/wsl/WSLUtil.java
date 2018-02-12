// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl;

import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

import static com.intellij.execution.wsl.WSLDistributionLegacy.LEGACY_WSL;

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

  private static final List<WSLDistribution> DISTRIBUTIONS = Arrays.asList(
    new WSLDistribution("UBUNTU", "Ubuntu", "ubuntu.exe", "Ubuntu"),
    new WSLDistribution("OPENSUSE42", "openSUSE-42", "opensuse-42.exe", "openSUSE Leap 42"),
    new WSLDistribution("SLES12", "SLES-12", "sles-12.exe", "SUSE Linux Enterprise Server 12"),
    LEGACY_WSL
  );

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
    return ContainerUtil.filter(DISTRIBUTIONS, dist -> dist.isAvailable());
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
}
