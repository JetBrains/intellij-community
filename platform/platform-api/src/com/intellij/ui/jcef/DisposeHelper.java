// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Used for thread-safe disposal.
 *
 * @author tav
 */
final class DisposeHelper {
  @NotNull private final AtomicBoolean myIsDisposed = new AtomicBoolean(false);

  public void dispose(@NotNull Runnable disposer) {
    if (!myIsDisposed.getAndSet(true)) disposer.run();
  }

  public boolean isDisposed() {
    return myIsDisposed.get();
  }
}
