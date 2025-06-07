// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.java.codeserver.core.JavaManifestUtil;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ManifestUtil {
  /**
   * Retrieve attribute value from module stored through build scripts.
   */
  @Contract(pure = true)
  public static @Nullable String lightManifestAttributeValue(@NotNull Module module, @NotNull String attribute) {
    return JavaManifestUtil.getManifestAttributeValue(module, attribute);
  }
}