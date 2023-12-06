// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.actions;

import com.intellij.openapi.util.NlsContexts.HintText;

public final class LayoutCodeInfoCollector {

  private @HintText String optimizeImportsNotification = null;
  private @HintText String reformatCodeNotification = null;
  private @HintText String rearrangeCodeNotification = null;
  private @HintText String secondFormatNotification = null;

  @HintText
  public String getOptimizeImportsNotification() {
    return optimizeImportsNotification;
  }

  public void setOptimizeImportsNotification(@HintText String optimizeImportsNotification) {
    this.optimizeImportsNotification = optimizeImportsNotification;
  }

  @HintText
  public String getReformatCodeNotification() {
    return reformatCodeNotification;
  }

  public void setReformatCodeNotification(@HintText String reformatCodeNotification) {
    this.reformatCodeNotification = reformatCodeNotification;
  }

  @HintText
  public String getRearrangeCodeNotification() {
    return rearrangeCodeNotification;
  }

  public void setRearrangeCodeNotification(@HintText String rearrangeCodeNotification) {
    this.rearrangeCodeNotification = rearrangeCodeNotification;
  }

  public boolean hasReformatOrRearrangeNotification() {
    return rearrangeCodeNotification != null
           || reformatCodeNotification != null;
  }

  @HintText
  public String getSecondFormatNotification() {
    return secondFormatNotification;
  }

  public void setSecondFormatNotification(@HintText String secondFormatNotification) {
    this.secondFormatNotification = secondFormatNotification;
  }

  public boolean isEmpty() {
    return optimizeImportsNotification == null
           && rearrangeCodeNotification == null
           && reformatCodeNotification == null;
  }
}
