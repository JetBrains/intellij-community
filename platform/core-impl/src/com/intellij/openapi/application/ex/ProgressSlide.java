// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.ex;

import org.jetbrains.annotations.NotNull;

public final class ProgressSlide {
  private final String myUrl;
  private final float myProgressRation;

  public ProgressSlide(@NotNull String url, float progressRatio) {
    myUrl = url;
    myProgressRation = progressRatio;
  }

  public float getProgressRation() {
    return myProgressRation;
  }

  public String getUrl() {
    return myUrl;
  }
}
