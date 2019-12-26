// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target;

import com.intellij.execution.Platform;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

public class TargetPlatform {
  public enum Arch {x32bit, x64bit}

  public static final TargetPlatform CURRENT = new TargetPlatform(Platform.current(), SystemInfo.is64Bit ? Arch.x64bit : Arch.x32bit);

  @NotNull private final Platform myPlatform;
  @NotNull private final Arch myArch;

  public TargetPlatform(@NotNull Platform platform, @NotNull Arch arch) {
    myPlatform = platform;
    myArch = arch;
  }

  @NotNull
  public Platform getPlatform() {
    return myPlatform;
  }

  @NotNull
  public Arch getArch() {
    return myArch;
  }
}


