// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target.value;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

class FixedTargetValue<T> implements TargetValue<T> {
  private final T myValue;

  FixedTargetValue(@NotNull T value) {
    myValue = value;
  }

  @Override
  public T getTargetValue() {
    return myValue;
  }

  @Override
  public T getLocalValue() {
    return myValue;
  }

  @Override
  public Promise<TargetValue<T>> promise() {
    return Promises.resolvedPromise(this);
  }
}
