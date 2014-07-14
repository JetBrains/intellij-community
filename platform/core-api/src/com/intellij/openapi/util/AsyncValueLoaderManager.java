package com.intellij.openapi.util;

import com.intellij.util.concurrency.AtomicFieldUpdater;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class AsyncValueLoaderManager<HOST, VALUE> {
  private final AtomicFieldUpdater<HOST, AsyncResult<VALUE>> fieldUpdater;

  @SuppressWarnings("UnusedDeclaration")
  public AsyncValueLoaderManager(@NotNull AtomicFieldUpdater<HOST, AsyncResult<VALUE>> fieldUpdater) {
    this.fieldUpdater = fieldUpdater;
  }

  public AsyncValueLoaderManager(@NotNull Class<HOST> ownerClass) {
    //noinspection unchecked
    fieldUpdater = ((AtomicFieldUpdater)AtomicFieldUpdater.forFieldOfType(ownerClass, AsyncResult.class));
  }

  public boolean isUpToDate(@NotNull HOST host, @NotNull VALUE value) {
    return true;
  }

  public abstract void load(@NotNull HOST host, @NotNull AsyncResult<VALUE> result);

  public final void reset(HOST host) {
    fieldUpdater.set(host, null);
  }

  public final void set(HOST host, @Nullable VALUE value) {
    if (value == null) {
      reset(host);
    }
    else {
      getOrCreateAsyncResult(host, false, false).setDone(value);
    }
  }

  public final boolean has(HOST host) {
    AsyncResult<VALUE> result = fieldUpdater.get(host);
    return result != null && result.isDone() && result.getResult() != null;
  }

  @NotNull
  public final AsyncResult<VALUE> get(HOST host) {
    return get(host, true);
  }

  public final AsyncResult<VALUE> get(HOST host, boolean checkFreshness) {
    return getOrCreateAsyncResult(host, checkFreshness, true);
  }

  private AsyncResult<VALUE> getOrCreateAsyncResult(HOST host, boolean checkFreshness, boolean load) {
    AsyncResult<VALUE> asyncResult = fieldUpdater.get(host);
    if (asyncResult == null) {
      if (!fieldUpdater.compareAndSet(host, null, asyncResult = new AsyncResult<VALUE>())) {
        return fieldUpdater.get(host);
      }
    }
    else if (!asyncResult.isProcessed()) {
      // if current asyncResult is not processed, so, we don't need to check cache state
      return asyncResult;
    }
    else if (asyncResult.isDone()) {
      if (!checkFreshness || isUpToDate(host, asyncResult.getResult())) {
        return asyncResult;
      }

      if (!fieldUpdater.compareAndSet(host, asyncResult, asyncResult = new AsyncResult<VALUE>())) {
        AsyncResult<VALUE> valueFromAnotherThread = fieldUpdater.get(host);
        while (valueFromAnotherThread == null) {
          if (fieldUpdater.compareAndSet(host, null, asyncResult)) {
            if (load) {
              load(host, asyncResult);
            }
            return asyncResult;
          }
          else {
            valueFromAnotherThread = fieldUpdater.get(host);
          }
        }
        return valueFromAnotherThread;
      }
    }
    if (load) {
      load(host, asyncResult);
    }
    return asyncResult;
  }
}