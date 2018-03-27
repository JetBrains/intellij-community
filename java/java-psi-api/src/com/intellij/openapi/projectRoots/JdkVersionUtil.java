/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.projectRoots;

import org.jetbrains.annotations.NotNull;

@Deprecated
public class JdkVersionUtil {
  /** @deprecated use {@link JavaSdkVersion#fromVersionString} (to be removed in IDEA 2019) */
  public static JavaSdkVersion getVersion(@NotNull String versionString) {
    return JavaSdkVersion.fromVersionString(versionString);
  }
}