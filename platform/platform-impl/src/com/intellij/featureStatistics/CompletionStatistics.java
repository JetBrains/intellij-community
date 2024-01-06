// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.featureStatistics;

public final class CompletionStatistics extends CumulativeStatistics {
  public int sparedCharacters = 0;

  public void registerInvocation(int spared) {
    registerInvocation();
    if (spared > 0) {
      sparedCharacters += spared;
    }
  }
}
