// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.artifacts.impl;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public final class JpsArtifactPathUtil {
  //todo[nik] copied from DeploymentUtil
  public static String trimForwardSlashes(@NotNull String path) {
    while (path.length() != 0 && (path.charAt(0) == '/' || path.charAt(0) == File.separatorChar)) {
      path = path.substring(1);
    }
    return path;
  }

  //todo[nik] copied from DeploymentUtil
  public static String appendToPath(@NotNull String basePath, @NotNull String relativePath) {
    final boolean endsWithSlash = StringUtilRt.endsWithChar(basePath, '/') || StringUtilRt.endsWithChar(basePath, '\\');
    final boolean startsWithSlash = StringUtil.startsWithChar(relativePath, '/') || StringUtil.startsWithChar(relativePath, '\\');
    String tail;
    if (endsWithSlash && startsWithSlash) {
      tail = trimForwardSlashes(relativePath);
    }
    else if (!endsWithSlash && !startsWithSlash && basePath.length() > 0 && relativePath.length() > 0) {
      tail = "/" + relativePath;
    }
    else {
      tail = relativePath;
    }
    return basePath + tail;
  }
}
