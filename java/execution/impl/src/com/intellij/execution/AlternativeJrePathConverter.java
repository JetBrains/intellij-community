// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.util.xmlb.Converter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JdkVersionDetector;

import java.nio.file.Path;

import static com.intellij.openapi.util.NullableLazyValue.lazyNullable;

public class AlternativeJrePathConverter extends Converter<String> {
  public static final NullableLazyValue<String> BUNDLED_JRE_PATH = lazyNullable(() -> {
    String jbr = Path.of(PathManager.getBundledRuntimePath()).toString();
    JdkVersionDetector.JdkVersionInfo versionInfo = JdkVersionDetector.getInstance().detectJdkVersionInfo(jbr);
    return versionInfo != null ? jbr : null;
  });

  private static final String BUNDLED = "BUNDLED";

  @Override
  public @Nullable String fromString(@NotNull String value) {
    if (BUNDLED.equals(value)) {
      String path = BUNDLED_JRE_PATH.getValue();
      if (path != null) return path;
    }
    return value;
  }

  @Override
  public @Nullable String toString(@NotNull String value) {
    String path = BUNDLED_JRE_PATH.getValue();
    return value.equals(path) ? BUNDLED : value;
  }
}
