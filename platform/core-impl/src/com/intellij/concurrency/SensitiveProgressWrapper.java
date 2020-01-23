// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.concurrency;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressWrapper;
import org.jetbrains.annotations.NotNull;

/**
 * A progress indicator wrapper, which reacts to its own cancellation in addition to the cancellation of its wrappee.
 */
public class SensitiveProgressWrapper extends ProgressWrapper {
  public SensitiveProgressWrapper(@NotNull ProgressIndicator indicator) {
    super(indicator, true);
  }
}