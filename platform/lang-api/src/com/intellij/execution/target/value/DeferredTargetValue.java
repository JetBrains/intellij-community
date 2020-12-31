// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target.value;

import com.intellij.execution.ExecutionBundle;
import com.intellij.lang.LangBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

public class DeferredTargetValue<T> implements TargetValue<T> {
  private final AsyncPromise<T> myTargetPromise = new AsyncPromise<>();
  private final Promise<T> myLocalPromise;

  public DeferredTargetValue(T localValue) {
    myLocalPromise = Promises.resolvedPromise(localValue);
  }

  public void resolve(T valueToResolve) {
    if (myTargetPromise.isDone()) {
      throw new IllegalStateException("Target value is already resolved to '" + myTargetPromise.get() + "'");
    }
    myTargetPromise.setResult(valueToResolve);
  }

  public void resolveFailure(@NotNull Throwable t) {
    if (myTargetPromise.isDone()) {
      throw new IllegalStateException("Target value is already resolved to '" + myTargetPromise.get() + "'");
    }
    myTargetPromise.setError(ExecutionBundle.message("message.error.happened.0", t.getMessage()));
  }

  public void stopProceeding() {
    if (myTargetPromise.isDone()) {
      throw new IllegalStateException("Target value is already resolved to '" + myTargetPromise.get() + "'");
    }
    myTargetPromise.setError(LangBundle.message("error.message.should.not.proceed.further"));
  }

  @Override
  public Promise<T> getLocalValue() {
    return myLocalPromise;
  }

  @Override
  public Promise<T> getTargetValue() {
    return myTargetPromise;
  }
}
