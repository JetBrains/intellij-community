// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.concurrency;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressWrapper;
import org.jetbrains.annotations.ApiStatus.Obsolete;
import org.jetbrains.annotations.NotNull;

/**
 * A progress indicator wrapper, which reacts to its own cancellation in addition to the cancellation of its wrappee.
 */
public class SensitiveProgressWrapper extends ProgressWrapper {

  @Obsolete
  public SensitiveProgressWrapper(@NotNull ProgressIndicator indicator) {
    super(indicator, true);
  }
}