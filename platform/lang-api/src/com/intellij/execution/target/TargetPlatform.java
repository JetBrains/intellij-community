// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target;

import com.intellij.execution.Platform;
import org.jetbrains.annotations.NotNull;

public class TargetPlatform {
  public static final TargetPlatform CURRENT = new TargetPlatform(Platform.current());

  private final Platform myPlatform;

  public TargetPlatform() {
    this(Platform.UNIX);
  }

  public TargetPlatform(@NotNull Platform platform) {
    myPlatform = platform;
  }

  public @NotNull Platform getPlatform() {
    return myPlatform;
  }
}
