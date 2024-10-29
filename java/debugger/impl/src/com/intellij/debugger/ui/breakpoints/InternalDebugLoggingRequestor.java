// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.breakpoints;

import org.jetbrains.annotations.ApiStatus;

/** It is used to investigate flaky test behavior in complex cases */
@ApiStatus.Internal
public interface InternalDebugLoggingRequestor {
  default boolean isDebugLogBreakpoint() {
    return false;
  }
}
