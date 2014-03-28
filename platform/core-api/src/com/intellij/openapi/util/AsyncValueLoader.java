package com.intellij.openapi.util;

import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

public abstract class AsyncValueLoader<T> {
  private final AtomicReference<AsyncResult<T>> ref = new AtomicReference<AsyncResult<T>>();

  private volatile long modificationCount;
  private volatile long loadedModificationCount;

  private final Runnable doneHandler = new Runnable() {
    @Override
    public void run() {
      loadedModificationCount = modificationCount;
    }
  };

  @NotNull
  public final AsyncResult<T> get() {
    return get(true);
  }

  public final void reset() {
    AsyncResult<T> oldValue = ref.getAndSet(null);
    if (oldValue != null) {
      rejectAndDispose(oldValue);
    }
  }

  private void rejectAndDispose(@NotNull AsyncResult<T> asyncResult) {
    try {
      if (!asyncResult.isProcessed()) {
        asyncResult.setRejected();
      }
    }
    finally {
      T result = asyncResult.getResult();
      if (result != null) {
        disposeResult(result);
      }
    }
  }

  protected void disposeResult(@NotNull T result) {
    if (result instanceof Disposable) {
      Disposer.dispose((Disposable)result, false);
    }
  }

  public final boolean has() {
    AsyncResult<T> result = ref.get();
    return result != null && result.isDone() && result.getResult() != null;
  }

  @NotNull
  public final AsyncResult<T> get(boolean checkFreshness) {
    AsyncResult<T> asyncResult = ref.get();
    if (asyncResult == null) {
      if (!ref.compareAndSet(null, asyncResult = new AsyncResult<T>())) {
        return ref.get();
      }
    }
    else if (!asyncResult.isProcessed()) {
      // if current asyncResult is not processed, so, we don't need to check cache state
      return asyncResult;
    }
    else if (asyncResult.isDone()) {
      if (!checkFreshness || isUpToDate(asyncResult.getResult())) {
        return asyncResult;
      }

      if (!ref.compareAndSet(asyncResult, asyncResult = new AsyncResult<T>())) {
        AsyncResult<T> valueFromAnotherThread = ref.get();
        while (valueFromAnotherThread == null) {
          if (ref.compareAndSet(null, asyncResult)) {
            callLoad(asyncResult);
            return asyncResult;
          }
          else {
            valueFromAnotherThread = ref.get();
          }
        }
        return valueFromAnotherThread;
      }
    }

    callLoad(asyncResult);
    return asyncResult;
  }

  /**
   * if result was rejected, by default this result will not be canceled - call get() will return rejected result instead of attempt to load again,
   * but you can change this behavior - return true if you want to cancel result on reject
   */
  protected boolean isCancelOnReject() {
    return false;
  }

  private void callLoad(final @NotNull AsyncResult<T> result) {
    if (isCancelOnReject()) {
      result.doWhenRejected(new Runnable() {
        @Override
        public void run() {
          ref.compareAndSet(result, null);
        }
      });
    }

    result.doWhenDone(doneHandler);

    try {
      load(result);
    }
    catch (Throwable e) {
      ref.compareAndSet(result, null);
      rejectAndDispose(result);
      //noinspection InstanceofCatchParameter
      throw e instanceof RuntimeException ? ((RuntimeException)e) : new RuntimeException(e);
    }
  }

  protected abstract void load(@NotNull AsyncResult<T> result) throws IOException;

  protected boolean isUpToDate(@NotNull T result) {
    return loadedModificationCount == modificationCount;
  }

  public final void set(@NotNull T result) {
    AsyncResult<T> oldValue = ref.getAndSet(AsyncResult.done(result));
    if (oldValue != null) {
      rejectAndDispose(oldValue);
    }
  }

  public final void markDirty() {
    modificationCount++;
  }
}