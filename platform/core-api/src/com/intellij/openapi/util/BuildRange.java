// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An open-ended range of build numbers.
 */
public class BuildRange {
  private final BuildNumber since;
  private final BuildNumber until;

  public BuildRange(@NotNull BuildNumber since, @NotNull BuildNumber until) {
    this.since = since;
    this.until = until;
    if (since.compareTo(until) > 0) {
      throw new IllegalArgumentException("Invalid range: [" + since + "; " + until + "]");
    }
  }

  public boolean inRange(@NotNull BuildNumber build) {
    return since.compareTo(build) <= 0 && build.compareTo(until) <= 0;
  }

  @Contract("null, _ -> null; _, null -> null")
  public static BuildRange fromStrings(@Nullable String sinceVal, @Nullable String untilVal) {
    BuildNumber since = BuildNumber.fromString(sinceVal);
    BuildNumber until = BuildNumber.fromString(untilVal);
    return since != null && until != null ? new BuildRange(since, until) : null;
  }
}