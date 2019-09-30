// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl;

import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.WindowsRegistryUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.intellij.execution.wsl.WSLUtil.LOG;

/**
 * Wraps {@link WSLDistribution} and fetches data from registry to find base path of distro on Windows
 */
public class WSLDistributionWithRoot extends WSLDistribution {
  private static final AtomicNotNullLazyValue<Map<String, String>> DISTRIBUTION_TO_ROOTFS =
    AtomicNotNullLazyValue.createValue(() -> {
      final Map<String, String> result = new HashMap<>();

      final String lxss = "HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Lxss";
      final List<String> distributions = WindowsRegistryUtil.readRegistryBranch(lxss);

      LOG.debug("Processing Lxss registry: " + distributions.size());
      for (String distribution : distributions) {
        final String name = WindowsRegistryUtil.readRegistryValue(lxss + "\\" + distribution, "DistributionName");
        final String path = WindowsRegistryUtil.readRegistryValue(lxss + "\\" + distribution, "BasePath");
        LOG.debug(name + ": " + path);

        if (path != null) {
          result.put(name, path + "\\rootfs");
        }
      }
      return Collections.unmodifiableMap(result);
    });
  @Nullable protected final String myWslRootInHost;

  public WSLDistributionWithRoot(@NotNull WSLDistribution wslDistribution) {
    super(wslDistribution);
    final File uncRoot = getUNCRoot();
    String wslRootInHost = uncRoot.exists() ? FileUtil.toSystemDependentName(uncRoot.getPath()) :
                           DISTRIBUTION_TO_ROOTFS.getValue().get(wslDistribution.getMsId());

    if (wslRootInHost == null) {
      LOG.warn("WSL (" + wslDistribution.getPresentableName() +") rootfs is null");
    }
    else if (!FileUtil.exists(wslRootInHost)) {
      LOG.warn("WSL rootfs doesn't exist: " + wslRootInHost);
      wslRootInHost = null;
    }
    myWslRootInHost = wslRootInHost;
  }

  @Nullable
  @Override
  public String getWslPath(@NotNull String windowsPath) {
    String canonicalPath = FileUtil.toCanonicalPath(windowsPath);
    if (myWslRootInHost != null && FileUtil.isAncestor(myWslRootInHost, canonicalPath, true)) {  // this is some internal WSL path
      return FileUtil.toSystemIndependentName(canonicalPath.substring(myWslRootInHost.length()));
    }
    return super.getWslPath(canonicalPath);
  }

  @Nullable
  @Override
  public String getWindowsPath(@NotNull String wslPath) {
    String windowsPath = super.getWindowsPath(wslPath);
    if (windowsPath != null) {
      return windowsPath;
    }

    if (myWslRootInHost == null) {
      return null;
    }
    return FileUtil.toSystemDependentName(myWslRootInHost + FileUtil.toCanonicalPath(wslPath));
  }
}
