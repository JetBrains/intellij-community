// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.openapi.progress;

import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated this interface is not used in the platform
 */
@ScheduledForRemoval
@Deprecated
@FunctionalInterface
public interface NonCancelableSection {
  void done();

  @NotNull
  NonCancelableSection EMPTY = () -> {
    // do nothing
  };
}
