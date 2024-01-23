// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress;

import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.ApiStatus.Obsolete;
import org.jetbrains.annotations.NotNull;

@Obsolete
public interface TaskInfo {
  @NotNull
  @NlsContexts.ProgressTitle
  String getTitle();

  @NlsContexts.Button
  String getCancelText();

  @NlsContexts.Tooltip
  String getCancelTooltipText();

  boolean isCancellable();

  default int getStatusBarIndicatorWeight() {
    return 1000;
  }
}
