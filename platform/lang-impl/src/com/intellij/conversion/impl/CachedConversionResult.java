// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.conversion.impl;

import com.intellij.openapi.application.PathManager;
import com.intellij.util.PathUtilRt;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;

@ApiStatus.Internal
public final class CachedConversionResult {
  static final String RELATIVE_PREFIX = "./";

  public final Set<String> appliedConverters;
  public final Object2LongMap<String> projectFilesTimestamps;

  CachedConversionResult(@NotNull Set<String> appliedConverters, @NotNull Object2LongMap<String> projectFilesTimestamps) {
    this.appliedConverters = appliedConverters;
    this.projectFilesTimestamps = projectFilesTimestamps;
  }

  static @NotNull Path getConversionInfoFile(@NotNull Path projectFile) {
    // https://youtrack.jetbrains.com/issue/IDEA-256011
    Path projectFileFileName = projectFile.getFileName();
    String dirName = PathUtilRt.suggestFileName((projectFileFileName == null ? "" : projectFileFileName.toString()) + Integer.toHexString(projectFile.toAbsolutePath().hashCode()));
    return Path.of(PathManager.getSystemPath(), "conversion", dirName + ".xml");
  }

  static @NotNull CachedConversionResult createEmpty() {
    return new CachedConversionResult(Collections.emptySet(), Object2LongMaps.emptyMap());
  }
}
