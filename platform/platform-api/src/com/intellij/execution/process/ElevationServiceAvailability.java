// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.process;

import com.intellij.execution.sudo.SudoCommandProvider;
import org.jetbrains.annotations.ApiStatus;

/**
 * @deprecated Use {@link SudoCommandProvider}
 */
@ApiStatus.Internal
@Deprecated
public interface ElevationServiceAvailability {
  boolean isAvailable();
}
