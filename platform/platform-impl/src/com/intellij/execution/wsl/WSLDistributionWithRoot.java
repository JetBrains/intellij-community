// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated use {@link WSLDistribution} directly
 */
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
public final class WSLDistributionWithRoot extends WSLDistribution {
  public WSLDistributionWithRoot(@NotNull WSLDistribution wslDistribution) {
    super(wslDistribution);
  }
}
