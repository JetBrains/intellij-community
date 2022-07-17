// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl;

import com.intellij.ide.macro.MacroPathConverter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

@ApiStatus.Internal
public class WslMacroPathConverter implements MacroPathConverter {
  private static final Logger LOG = Logger.getInstance(WslMacroPathConverter.class);

  private final WSLDistribution myWsl;

  public WslMacroPathConverter(@NotNull WSLDistribution wsl) {
    myWsl = wsl;
  }

  @Override
  public @NotNull String convertPath(@NotNull String path) {
    try {
      String converted = myWsl.getWslPath(path);
      return converted != null ? converted : path;
    }
    catch (IllegalArgumentException e) {
      LOG.warn("Failed to convert to path: " + path, e);
      return path;
    }
  }

  @Override
  public @NotNull String convertPathList(@NotNull String pathList) {
    List<String> paths = StringUtil.split(pathList, File.pathSeparator);
    return Strings.join(ContainerUtil.map(paths, p -> convertPath(p)), ":");
  }

}
