// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.concurrency;

import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.InternalPromiseUtil.PromiseValue;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import static org.jetbrains.concurrency.InternalPromiseUtil.CANCELLED_PROMISE;
import static org.jetbrains.concurrency.InternalPromiseUtil.isHandlerObsolete;

class DonePromise<T> extends InternalPromiseUtil.BasePromise<T> {
  private final PromiseValue<T> value;

  public DonePromise(@NotNull PromiseValue<T> value) {
    this.value = value;
  }

  @NotNull
  @Override
  public Promise<T> onSuccess(@NotNull Consumer<? super T> handler) {
    if (value.error != null) {
      return this;
    }

    if (!isHandlerObsolete(handler)) {
      handler.accept(value.result);
    }
    return this;
  }

  @NotNull
  @Override
  public Promise<T> processed(@NotNull Promise<? super T> child) {
    //noinspection unchecked
    ((InternalPromiseUtil.PromiseImpl<T>)child)._setValue(value);
    return this;
  }

  @NotNull
  @Override
  public Promise<T> onProcessed(@NotNull Consumer<? super T> handler) {
    if (value.error == null) {
      onSuccess(handler);
    }
    else if (!isHandlerObsolete(handler)) {
      handler.accept(null);
    }
    return this;
  }

  @NotNull
  @Override
  public Promise<T> onError(@NotNull Consumer<Throwable> handler) {
    if (value.error != null && !isHandlerObsolete(handler)) {
      handler.accept(value.error);
    }
    return this;
  }

  @NotNull
  @Override
  public <SUB_RESULT> Promise<SUB_RESULT> then(@NotNull Function<? super T, ? extends SUB_RESULT> done) {
    if (value.error != null) {
      //noinspection unchecked
      return (Promise<SUB_RESULT>)this;
    }
    else if (isHandlerObsolete(done)) {
      //noinspection unchecked
      return (Promise<SUB_RESULT>)CANCELLED_PROMISE.getValue();
    }
    else {
      return new DonePromise<>(PromiseValue.createFulfilled(done.fun(value.result)));
    }
  }

  @NotNull
  @Override
  public <SUB_RESULT> Promise<SUB_RESULT> thenAsync(@NotNull Function<? super T, Promise<SUB_RESULT>> done) {
    if (value.error == null) {
      return done.fun(value.result);
    }
    else {
      //noinspection unchecked
      return (Promise<SUB_RESULT>)this;
    }
  }

  @Nullable
  @Override
  public T blockingGet(int timeout, @NotNull TimeUnit timeUnit) throws ExecutionException, TimeoutException {
    return value.getResultOrThrowError();
  }

  @Override
  public void _setValue(@NotNull PromiseValue<T> value) {
  }

  @Nullable
  @Override
  protected PromiseValue<T> getValue() {
    return value;
  }

  @Override
  public void cancel() {
  }
}