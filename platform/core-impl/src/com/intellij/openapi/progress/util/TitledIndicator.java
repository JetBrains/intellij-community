// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.util;

import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface TitledIndicator {
  void setTitle(@NotNull @NlsContexts.ProgressTitle String title);
  @NlsContexts.ProgressTitle String getTitle();
}
