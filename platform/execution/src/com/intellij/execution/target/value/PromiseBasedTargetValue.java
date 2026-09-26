// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target.value;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

class PromiseBasedTargetValue<V> implements TargetValue<V> {
  private final Promise<V> myLocalPromise;
  private final Promise<V> myTargetPromise;

  PromiseBasedTargetValue(@NotNull V localValue, @NotNull Promise<V> targetPromise) {
    myLocalPromise = Promises.resolvedPromise(localValue);
    myTargetPromise = targetPromise;
  }

  @Override
  public Promise<V> getLocalValue() {
    return myLocalPromise;
  }

  @Override
  public Promise<V> getTargetValue() {
    return myTargetPromise;
  }
}
