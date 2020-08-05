// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public final class JdkVersionUtil {
  /** @deprecated use {@link JavaSdkVersion#fromVersionString} */
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
  @Deprecated
  public static JavaSdkVersion getVersion(@NotNull String versionString) {
    return JavaSdkVersion.fromVersionString(versionString);
  }
}