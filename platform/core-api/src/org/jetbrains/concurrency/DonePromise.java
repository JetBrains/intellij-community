// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.concurrency;

import com.intellij.openapi.util.Getter;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.InternalPromiseUtil.*;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import static org.jetbrains.concurrency.InternalPromiseUtil.*;

class DonePromise<T> implements Getter<T>, Promise<T>, Future<T>, InternalPromiseUtil.PromiseImpl<T> {
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

  @NotNull
  @Override
  public State getState() {
    return value.getState();
  }

  @Nullable
  @Override
  public T blockingGet(int timeout, @NotNull TimeUnit timeUnit) throws ExecutionException, TimeoutException {
    Throwable error = value.error;
    if (error == null) {
      return value.result;
    }

    ExceptionUtil.rethrowUnchecked(error);
    if (error instanceof ExecutionException) {
      throw ((ExecutionException)error);
    }
    if (error instanceof TimeoutException) {
      throw ((TimeoutException)error);
    }
    throw new ExecutionException(error);
  }

  @Override
  public boolean isDone() {
    return true;
  }

  @Override
  public T get() {
    return value.result;
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    return false;
  }

  @Override
  public boolean isCancelled() {
    return value.error == OBSOLETE_ERROR;
  }

  @Override
  public T get(long timeout, @NotNull TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
    return value.result;
  }

  @Override
  public void _setValue(@NotNull PromiseValue<T> value) {
  }
}