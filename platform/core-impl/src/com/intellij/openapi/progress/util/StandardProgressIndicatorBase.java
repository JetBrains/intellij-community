// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress.util;

import com.intellij.openapi.progress.StandardProgressIndicator;

public final class StandardProgressIndicatorBase extends AbstractProgressIndicatorBase implements StandardProgressIndicator {
  @Override
  public final void cancel() {
    super.cancel();
  }

  @Override
  public final boolean isCanceled() {
    return super.isCanceled();
  }
}
