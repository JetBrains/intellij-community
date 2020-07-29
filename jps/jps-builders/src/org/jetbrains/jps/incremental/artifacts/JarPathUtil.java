// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.artifacts;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public final class JarPathUtil {
  public static final String JAR_SEPARATOR = "!/";

  @NotNull
  public static File getLocalFile(@NotNull String fullPath) {
    final int i = fullPath.indexOf(JAR_SEPARATOR);
    String filePath = i == -1 ? fullPath : fullPath.substring(0, i);
    return new File(FileUtil.toSystemDependentName(filePath));
  }
}
