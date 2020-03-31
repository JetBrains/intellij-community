// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target.value;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

class FixedTargetValue<T> implements TargetValue<T> {
  private final Promise<T> myPromise;

  FixedTargetValue(@NotNull T value) {
    myPromise = Promises.resolvedPromise(value);
  }

  @Override
  public Promise<T> getTargetValue() {
    return myPromise;
  }

  @Override
  public Promise<T> getLocalValue() {
    return myPromise;
  }
}
