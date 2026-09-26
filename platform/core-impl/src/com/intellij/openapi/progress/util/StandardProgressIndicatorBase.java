// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.util;

import com.intellij.openapi.progress.StandardProgressIndicator;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.ApiStatus.Obsolete;

@ApiStatus.Internal
public final class StandardProgressIndicatorBase extends AbstractProgressIndicatorBase implements StandardProgressIndicator {

  @Obsolete
  public StandardProgressIndicatorBase() {
  }
}
