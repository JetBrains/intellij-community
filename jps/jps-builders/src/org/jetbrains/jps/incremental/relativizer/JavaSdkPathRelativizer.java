// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.relativizer;

import com.intellij.util.lang.JavaVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.library.sdk.JpsSdk;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class JavaSdkPathRelativizer implements PathRelativizer {
  @Nullable private Map<String, String> myJavaSdkPathMap;

  JavaSdkPathRelativizer(@Nullable Set<JpsSdk<?>> javaSdks) {
    if (javaSdks != null) {
      myJavaSdkPathMap = javaSdks.stream()
        .collect(Collectors.toMap(sdk -> {
          JavaVersion version = JavaVersion.tryParse(sdk.getVersionString());
          return "$JDK_" + (version != null ? version.toString() : "0") + "$";
        }, sdk -> sdk.getHomePath()));
    }
  }

  @Override
  public boolean isAcceptableAbsolutePath(@NotNull String path) {
    return myJavaSdkPathMap != null &&
           !myJavaSdkPathMap.isEmpty() &&
           myJavaSdkPathMap.values().stream().anyMatch(sdkPath -> path.contains(sdkPath));
  }

  @Override
  public boolean isAcceptableRelativePath(@NotNull String path) {
    return myJavaSdkPathMap != null &&
           !myJavaSdkPathMap.isEmpty() &&
           myJavaSdkPathMap.keySet().stream().anyMatch(identifier -> path.contains(identifier));
  }

  @Override
  public String toRelativePath(@NotNull String path) {
    if (myJavaSdkPathMap == null || myJavaSdkPathMap.isEmpty()) return path;
    Optional<Map.Entry<String, String>> optionalEntry = myJavaSdkPathMap.entrySet().stream()
      .filter(it -> path.contains(it.getValue())).findFirst();
    if (!optionalEntry.isPresent()) return path;

    Map.Entry<String, String> javaSdkEntry = optionalEntry.get();
    int i = path.indexOf(javaSdkEntry.getValue());
    if (i < 0) return path;
    return javaSdkEntry.getKey() + path.substring(i + javaSdkEntry.getValue().length());
  }

  @Override
  public String toAbsolutePath(@NotNull String path) {
    if (myJavaSdkPathMap == null || myJavaSdkPathMap.isEmpty()) return path;
    Optional<Map.Entry<String, String>> optionalEntry = myJavaSdkPathMap.entrySet().stream()
      .filter(it -> path.contains(it.getKey())).findFirst();
    if (!optionalEntry.isPresent()) return path;

    Map.Entry<String, String> javaSdkEntry = optionalEntry.get();
    int i = path.indexOf(javaSdkEntry.getKey());
    if (i < 0) return path;
    return javaSdkEntry.getValue() + path.substring(i + javaSdkEntry.getKey().length());
  }
}
