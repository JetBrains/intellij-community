/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.concurrency;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public abstract class Promise<T> {
  public static final Promise<Void> DONE = new DonePromise<Void>(null);
  public static final Promise<Void> REJECTED = new RejectedPromise<Void>(createError("rejected"));

  @NotNull
  public static RuntimeException createError(@NotNull String error) {
    return new MessageError(error);
  }

  public enum State {
    PENDING, FULFILLED, REJECTED
  }

  @NotNull
  public static <T> Promise<T> resolve(T result) {
    if (result == null) {
      //noinspection unchecked
      return (Promise<T>)DONE;
    }
    else {
      return new DonePromise<T>(result);
    }
  }

  @NotNull
  public static <T> Promise<T> reject(@NotNull String error) {
    return reject(createError(error));
  }

  @NotNull
  public static <T> Promise<T> reject(@Nullable Throwable error) {
    if (error == null) {
      //noinspection unchecked
      return (Promise<T>)REJECTED;
    }
    else {
      return new RejectedPromise<T>(error);
    }
  }

  @NotNull
  public static Promise<?> all(@NotNull Collection<Promise<?>> promises) {
    if (promises.size() == 1) {
      return promises instanceof List ? ((List<Promise<?>>)promises).get(0) : promises.iterator().next();
    }
    else {
      return all(promises, null);
    }
  }

  @NotNull
  public static <T> Promise<T> all(@NotNull Collection<Promise<?>> promises, @Nullable T totalResult) {
    if (promises.isEmpty()) {
      //noinspection unchecked
      return (Promise<T>)DONE;
    }

    final AsyncPromise<T> totalPromise = new AsyncPromise<T>();
    Consumer done = new CountDownConsumer<T>(promises.size(), totalPromise, totalResult);
    Consumer<Throwable> rejected = new Consumer<Throwable>() {
      @Override
      public void consume(Throwable error) {
        totalPromise.setError(error);
      }
    };

    for (Promise<?> promise : promises) {
      //noinspection unchecked
      promise.done(done);
      promise.rejected(rejected);
    }
    return totalPromise;
  }

  @NotNull
  public static Promise<Void> wrapAsVoid(@NotNull ActionCallback asyncResult) {
    final AsyncPromise<Void> promise = new AsyncPromise<Void>();
    asyncResult.doWhenDone(new Runnable() {
      @Override
      public void run() {
        promise.setResult(null);
      }
    }).doWhenRejected(new Consumer<String>() {
      @Override
      public void consume(String error) {
        promise.setError(createError(error == null ? "Internal error" : error));
      }
    });
    return promise;
  }

  @NotNull
  public static <T> Promise<T> wrap(@NotNull AsyncResult<T> asyncResult) {
    final AsyncPromise<T> promise = new AsyncPromise<T>();
    asyncResult.doWhenDone(new Consumer<T>() {
      @Override
      public void consume(T result) {
        promise.setResult(result);
      }
    }).doWhenRejected(new Consumer<String>() {
      @Override
      public void consume(String error) {
        promise.setError(error);
      }
    });
    return promise;
  }

  @NotNull
  public abstract Promise<T> done(@NotNull Consumer<T> done);

  @NotNull
  public abstract Promise<T> processed(@NotNull AsyncPromise<T> fulfilled);

  @NotNull
  public abstract Promise<T> rejected(@NotNull Consumer<Throwable> rejected);

  public abstract Promise<T> processed(@NotNull Consumer<T> processed);

  @NotNull
  public abstract <SUB_RESULT> Promise<SUB_RESULT> then(@NotNull Function<T, SUB_RESULT> done);

  @NotNull
  public abstract <SUB_RESULT> Promise<SUB_RESULT> then(@NotNull AsyncFunction<T, SUB_RESULT> done);

  @NotNull
  public abstract State getState();

  @SuppressWarnings("ExceptionClassNameDoesntEndWithException")
  public static class MessageError extends RuntimeException {
    private final boolean log;

    public MessageError(@NotNull String error) {
      this(error, false);
    }

    public MessageError(@NotNull String error, boolean log) {
      super(error);

      this.log = log;
    }

    @NotNull
    @Override
    public final synchronized Throwable fillInStackTrace() {
      return this;
    }
  }

  /**
   * Log error if not message error
   */
  public static void logError(@NotNull Logger logger, @NotNull Throwable e) {
    if (!(e instanceof ProcessCanceledException) &&
        (!(e instanceof MessageError) || ((MessageError)e).log || ApplicationManager.getApplication().isUnitTestMode())) {
      logger.error(e);
    }
  }

  public abstract void notify(@NotNull AsyncPromise<T> child);
}