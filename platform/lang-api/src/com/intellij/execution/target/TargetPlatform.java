// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target;

import com.intellij.execution.Platform;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TargetPlatform {
  public static final TargetPlatform CURRENT = new TargetPlatform(SystemInfo.OS_NAME, SystemInfo.OS_VERSION, SystemInfo.OS_ARCH);

  @Nullable
  private final String myOs;
  @Nullable
  private final String myOsVersion;
  @Nullable
  private final String myArch;

  public TargetPlatform(@Nullable String os, @Nullable String osVersion, @Nullable String arch) {
    myOs = os;
    myOsVersion = osVersion;
    myArch = arch;
  }

  @Nullable
  public String getOs() {
    return myOs;
  }

  @Nullable
  public String getOsVersion() {
    return myOsVersion;
  }

  @Nullable
  public String getArch() {
    return myArch;
  }

  @NotNull
  public Platform getPlatform() {
    return myOs != null && myOs.startsWith("windows") ? Platform.WINDOWS : Platform.UNIX;
  }
}


