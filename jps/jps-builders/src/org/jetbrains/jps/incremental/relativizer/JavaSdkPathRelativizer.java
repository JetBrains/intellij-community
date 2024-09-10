// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.relativizer;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.lang.JavaVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.library.sdk.JpsSdk;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * For now, it doesn't convert any paths related to JDK while producing portable caches, because
 * they skipped in {@link org.jetbrains.jps.incremental.ModuleBuildTarget#getDependenciesFingerprint}. But it stays to be included in
 * {@link PathRelativizerService} to get an opportunity to handle such paths due to manual call of
 * {@link PathRelativizerService#toRelative} or {@link PathRelativizerService#toFull} with any path.
 */
final class JavaSdkPathRelativizer implements PathRelativizer {
  private @Nullable Map<String, String> javaSdkPathMap;

  JavaSdkPathRelativizer(@Nullable Set<? extends JpsSdk<?>> javaSdks) {
    if (javaSdks != null) {
      javaSdkPathMap = javaSdks.stream()
        .collect(Collectors.toMap(sdk -> {
          JavaVersion version = JavaVersion.tryParse(sdk.getVersionString());
          return "$JDK_" + (version != null ? version.toString() : "0") + "$";
        }, sdk -> PathRelativizerService.normalizePath(sdk.getHomePath()), (sdk1, sdk2) -> sdk1));
    }
  }

  @Override
  public @Nullable String toRelativePath(@NotNull String path) {
    if (javaSdkPathMap == null || javaSdkPathMap.isEmpty()) {
      return null;
    }

    for (Map.Entry<String, String> entry : javaSdkPathMap.entrySet()) {
      if (FileUtil.startsWith(path, entry.getValue())) {
        return entry.getKey() + path.substring(entry.getValue().length());
      }
    }
    return null;
  }

  @Override
  public @Nullable String toAbsolutePath(@NotNull String path) {
    if (javaSdkPathMap == null || javaSdkPathMap.isEmpty()) {
      return null;
    }

    for (Map.Entry<String, String> it : javaSdkPathMap.entrySet()) {
      if (path.startsWith(it.getKey())) {
        return it.getValue() + path.substring(it.getKey().length());
      }
    }
    return null;
  }
}
