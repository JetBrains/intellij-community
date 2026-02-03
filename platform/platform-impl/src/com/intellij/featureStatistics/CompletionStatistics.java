// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.featureStatistics;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class CompletionStatistics extends CumulativeStatistics {
  public int sparedCharacters = 0;

  public void registerInvocation(int spared) {
    registerInvocation();
    if (spared > 0) {
      sparedCharacters += spared;
    }
  }
}
