package com.intellij.openapi.util;

import com.intellij.openapi.util.AsyncResult;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

@SuppressWarnings("unchecked")
public abstract class AsyncValueLoaderManager<HOST, VALUE> {
  private final AtomicReferenceFieldUpdater<HOST, AsyncResult> fieldUpdater;

  public AsyncValueLoaderManager(AtomicReferenceFieldUpdater<HOST, AsyncResult> fieldUpdater) {
    this.fieldUpdater = fieldUpdater;
  }

  public boolean checkFreshness(HOST host, VALUE value) {
    return true;
  }

  public abstract void load(HOST host, AsyncResult<VALUE> result);

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
      if (!checkFreshness || checkFreshness(host, asyncResult.getResult())) {
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