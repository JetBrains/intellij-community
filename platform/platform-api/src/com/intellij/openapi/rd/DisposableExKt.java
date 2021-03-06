// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.rd;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.jetbrains.rd.util.lifetime.Lifetime;
import org.jetbrains.annotations.ApiStatus;

public final class DisposableExKt {
  private DisposableExKt() {}

  /**
   * @deprecated Use {@link com.intellij.openapi.rd.LifetimeDisposableExKt#createLifetime(Disposable)}
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.2")
  @Deprecated
  public static Lifetime createLifetime(Disposable disposable) {
    return DisposableEx.defineNestedLifetime(disposable).getLifetime();
  }

  /**
   * @deprecated Use {@link Disposer#register(Disposable, Disposable)}
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.3")
  @Deprecated
  public static void attachChild(Disposable parent, Disposable child) {
    Disposer.register(parent, child);
  }
}
