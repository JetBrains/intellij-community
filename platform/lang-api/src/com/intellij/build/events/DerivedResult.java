// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.build.events;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * result status of task is derived by its children.
 *
 */
@ApiStatus.Experimental
public interface DerivedResult extends EventResult {
  @NotNull
  FailureResult createFailureResult();

  @NotNull
  EventResult createSuccessResult();
}

