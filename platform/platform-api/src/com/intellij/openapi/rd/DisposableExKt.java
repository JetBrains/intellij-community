// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.rd;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.jetbrains.rd.util.lifetime.Lifetime;
import com.jetbrains.rd.util.lifetime.LifetimeDefinition;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.ApiStatus;

public final class DisposableExKt {
  private DisposableExKt() {}

  /**
   * @deprecated Use version from `LifetimeDisposableEx`
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.2")
  @Deprecated
  public static LifetimeDefinition defineNestedLifetime(Disposable disposable) {
    return DisposableEx.defineNestedLifetime(disposable);
  }

  /**
   * @deprecated Use version from `LifetimeDisposableEx`
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.2")
  @Deprecated
  public static Lifetime createLifetime(Disposable disposable) {
    return DisposableEx.defineNestedLifetime(disposable).getLifetime();
  }

  /**
   * @deprecated Use version from `LifetimeDisposableEx`
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.2")
  @Deprecated
  public static void doIfAlive(Disposable disposable, Function1<Lifetime, Unit> action) {
    DisposableEx.doIfAlive(disposable, action);
  }

  /**
   * @deprecated Use version from `LifetimeDisposableEx`
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.2")
  @Deprecated
  public static Disposable createNestedDisposable(Lifetime lifetime) {
    return createNestedDisposable(lifetime, "lifetimeToDisposable");
  }

  /**
   * @deprecated Use version from `LifetimeDisposableEx`
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.2")
  @Deprecated
  public static Disposable createNestedDisposable(Lifetime lifetime, String debugName) {
    return DisposableEx.createNestedDisposable(lifetime, debugName);
  }

  /**
   * @deprecated Use version from `LifetimeDisposableEx`
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.2")
  @Deprecated
  public static Disposable createNestedDisposable$default(Lifetime lifetime, String debugName, int intArg, Object ignored) {
    if((intArg & 1) != 0)
      debugName = "lifetimeToDisposable";
    return createNestedDisposable(lifetime, debugName);
  }

  /**
   * @deprecated Use version from `DisposableEx`
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.3")
  @Deprecated
  public static void attachChild(Disposable parent, Disposable child) {
    Disposer.register(parent, child);
  }

  /**
   * @deprecated Use version from `DisposableEx`
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.3")
  @Deprecated
  public static void attach(Disposable parent, Function0<Unit> disposable) {
    DisposableEx.attach(parent, disposable);
  }
}
