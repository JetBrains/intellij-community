// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.util;

import com.intellij.openapi.progress.StandardProgressIndicator;
import org.jetbrains.annotations.ApiStatus.Obsolete;

public class ProgressIndicatorBase extends AbstractProgressIndicatorExBase implements StandardProgressIndicator {

  @Obsolete
  public ProgressIndicatorBase() {
    super();
  }

  @Obsolete
  public ProgressIndicatorBase(boolean reusable) {
    super(reusable);
  }

  @Obsolete
  public ProgressIndicatorBase(boolean reusable, boolean allowSystemActivity) {
    super(reusable);
    if (!allowSystemActivity) dontStartActivity();
  }

  @Override
  public final void cancel() {
    super.cancel();
  }

  @Override
  public final boolean isCanceled() {
    return super.isCanceled();
  }
}
