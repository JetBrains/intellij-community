// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target.value;

import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

public class DeferredLocalTargetValue<T> implements TargetValue<T> {
  private final AsyncPromise<T> myLocalPromise = new AsyncPromise<>();
  private final Promise<T> myTargetValue;

  public DeferredLocalTargetValue(T targetValue) {
    myTargetValue = Promises.resolvedPromise(targetValue);
  }

  public void resolve(T valueToResolve) {
    if (myLocalPromise.isDone()) {
      throw new IllegalStateException("Local value is already resolved to '" + myLocalPromise.get() + "'");
    }
    myLocalPromise.setResult(valueToResolve);
  }

  @Override
  public Promise<T> getLocalValue() {
    return myLocalPromise;
  }

  @Override
  public Promise<T> getTargetValue() {
    return myTargetValue;
  }
}
