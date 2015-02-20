package org.jetbrains.concurrency;

import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

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
  public static Promise<Void> all(@NotNull Collection<Promise<?>> promises) {
    return all(promises, null);
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
        if (totalPromise.state == AsyncPromise.State.PENDING) {
          totalPromise.setError(error);
        }
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
        promise.setError(createError(error));
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
        promise.setError(createError(error));
      }
    });
    return promise;
  }

  @NotNull
  public abstract Promise<T> done(@NotNull Consumer<T> done);

  @NotNull
  public abstract Promise<T> processed(@NotNull final AsyncPromise<T> fulfilled);

  @NotNull
  public abstract Promise<T> rejected(@NotNull Consumer<Throwable> rejected);

  public abstract Promise<T> processed(@NotNull Consumer<T> processed);

  @NotNull
  public abstract <SUB_RESULT> Promise<SUB_RESULT> then(@NotNull Function<T, SUB_RESULT> done);

  //@NotNull
  //public abstract <SUB_RESULT> Promise<SUB_RESULT> then(@NotNull PairConsumer<T, AsyncPromise<SUB_RESULT>> done);

  //public final <SUB_RESULT> Promise<SUB_RESULT> then(@NotNull final SUB_RESULT result) {
  //  return then(new Function<T, SUB_RESULT>() {
  //    @Override
  //    public SUB_RESULT fun(T ignored) {
  //      return result;
  //    }
  //  });
  //}

  @NotNull
  public abstract <SUB_RESULT> Promise<SUB_RESULT> then(@NotNull AsyncFunction<T, SUB_RESULT> done);

  @NotNull
  public abstract State getState();

  public final void notify(@NotNull final AsyncResult<T> result) {
    done(new Consumer<T>() {
      @Override
      public void consume(T t) {
        result.setDone(t);
      }
    });
    rejected(new Consumer<Throwable>() {
      @Override
      public void consume(Throwable error) {
        result.reject(error == null ? null : error.getMessage());
      }
    });
  }

  @SuppressWarnings("ExceptionClassNameDoesntEndWithException")
  public static class MessageError extends RuntimeException {
    public MessageError(@NotNull String error) {
      super(error);
    }

    @NotNull
    @Override
    public final synchronized Throwable fillInStackTrace() {
      return this;
    }
  }

  abstract void notify(@NotNull AsyncPromise<T> child);
}