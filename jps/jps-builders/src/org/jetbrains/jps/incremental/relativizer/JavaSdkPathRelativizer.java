// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.relativizer;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.lang.JavaVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.library.sdk.JpsSdk;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * For now, it doesn't convert any paths related to JDK  during producing portable caches, because
 * they skipped in {@link org.jetbrains.jps.incremental.ModuleBuildTarget#getDependenciesFingerprint}. But it stays to be included in
 * {@link PathRelativizerService} to get an opportunity to handle such paths due to manual call of
 * {@link PathRelativizerService#toRelative} or {@link PathRelativizerService#toFull} with any path.
 */
class JavaSdkPathRelativizer implements PathRelativizer {
  @Nullable private Map<String, String> myJavaSdkPathMap;

  JavaSdkPathRelativizer(@Nullable Set<? extends JpsSdk<?>> javaSdks) {
    if (javaSdks != null) {
      myJavaSdkPathMap = javaSdks.stream()
        .collect(Collectors.toMap(sdk -> {
          JavaVersion version = JavaVersion.tryParse(sdk.getVersionString());
          return "$JDK_" + (version != null ? version.toString() : "0") + "$";
        }, sdk -> PathRelativizerService.normalizePath(sdk.getHomePath()), (sdk1, sdk2) -> sdk1));
    }
  }

  @Nullable
  @Override
  public String toRelativePath(@NotNull String path) {
    if (myJavaSdkPathMap == null || myJavaSdkPathMap.isEmpty()) return null;
    Optional<Map.Entry<String, String>> optionalEntry = myJavaSdkPathMap.entrySet().stream()
      .filter(entry -> FileUtil.startsWith(path, entry.getValue())).findFirst();
    if (optionalEntry.isEmpty()) return null;

    Map.Entry<String, String> javaSdkEntry = optionalEntry.get();
    return javaSdkEntry.getKey() + path.substring(javaSdkEntry.getValue().length());
  }

  @Nullable
  @Override
  public String toAbsolutePath(@NotNull String path) {
    if (myJavaSdkPathMap == null || myJavaSdkPathMap.isEmpty()) return null;
    Optional<Map.Entry<String, String>> optionalEntry = myJavaSdkPathMap.entrySet().stream()
      .filter(it -> path.startsWith(it.getKey())).findFirst();
    if (optionalEntry.isEmpty()) return null;

    Map.Entry<String, String> javaSdkEntry = optionalEntry.get();
    return javaSdkEntry.getValue() + path.substring(javaSdkEntry.getKey().length());
  }
}
