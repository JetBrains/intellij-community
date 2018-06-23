// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @deprecated Use Promise
 * @param <T>
 */
@Deprecated
public class AsyncResult<T> extends ActionCallback {
  protected T myResult;

  public AsyncResult() {
  }

  @NotNull
  public AsyncResult<T> setDone(T result) {
    myResult = result;
    setDone();
    return this;
  }

  @NotNull
  public AsyncResult<T> setRejected(T result) {
    myResult = result;
    setRejected();
    return this;
  }

  /**
   * @deprecated Use {@link #doWhenDone(Consumer)} (to remove in IDEA 16)
   */
  @NotNull
  @Deprecated
  public AsyncResult<T> doWhenDone(@SuppressWarnings("deprecation") @NotNull final Handler<T> handler) {
    doWhenDone(() -> handler.run(myResult));
    return this;
  }

  @NotNull
  public AsyncResult<T> doWhenDone(@NotNull final Consumer<T> consumer) {
    doWhenDone(() -> consumer.consume(myResult));
    return this;
  }

  @Override
  @NotNull
  public final AsyncResult<T> notify(@NotNull final ActionCallback child) {
    super.notify(child);
    return this;
  }

  public T getResult() {
    return myResult;
  }

  public T getResultSync() {
    return getResultSync(-1);
  }

  @Nullable
  public T getResultSync(long msTimeout) {
    waitFor(msTimeout);
    return myResult;
  }

  /**
   * @deprecated Use {@link Consumer} (to remove in IDEA 16)
   */
  @Deprecated
  public interface Handler<T> {
    void run(T t);
  }

  @NotNull
  public static <R> AsyncResult<R> done(@Nullable R result) {
    return new AsyncResult<R>().setDone(result);
  }
}
