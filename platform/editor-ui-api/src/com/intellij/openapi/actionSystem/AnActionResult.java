// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class AnActionResult {

  @ApiStatus.Internal
  public static final AnActionResult IGNORED = new AnActionResult(null);
  @ApiStatus.Internal
  public static final AnActionResult PERFORMED = new AnActionResult(null);
  @ApiStatus.Internal
  public static @NotNull AnActionResult failed(@NotNull Throwable cause) {
    return new AnActionResult(cause);
  }

  private final Throwable myFailureCause;

  AnActionResult(@Nullable Throwable failureCause) {
    myFailureCause = failureCause;
  }

  public boolean isPerformed() {
    return this == PERFORMED;
  }

  @ApiStatus.Internal
  public @Nullable Throwable getFailureCause() {
    if (isPerformed()) {
      throw new AssertionError("not a failure");
    }
    return myFailureCause;
  }
}
