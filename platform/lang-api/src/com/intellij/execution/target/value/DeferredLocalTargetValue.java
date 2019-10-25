// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target.value;

import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;

public class DeferredLocalTargetValue<T> implements TargetValue<T> {
  private final AsyncPromise<TargetValue<T>> myPromise = new AsyncPromise<>();
  private final T myTargetValue;

  private T myResolvedValue;

  public DeferredLocalTargetValue(T targetValue) {
    myTargetValue = targetValue;
  }

  public void resolve(T valueToResolve) {
    if (myPromise.isDone()) {
      throw new IllegalStateException("Local value is already resolved to '" + myResolvedValue + "'");
    }
    myResolvedValue = valueToResolve;
    myPromise.setResult(this);
  }

  @Override
  public T getLocalValue() {
    if (!myPromise.isDone()) {
      throw new IllegalStateException("Target value '" + myTargetValue + "' has not been resolved yet");
    }
    return myResolvedValue;
  }

  @Override
  public T getTargetValue() {
    return myTargetValue;
  }

  @Override
  public Promise<TargetValue<T>> promise() {
    return myPromise;
  }
}
