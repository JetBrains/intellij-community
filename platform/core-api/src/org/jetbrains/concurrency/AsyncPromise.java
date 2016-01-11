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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Getter;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class AsyncPromise<T> extends Promise<T> implements Getter<T> {
  private static final Logger LOG = Logger.getInstance(AsyncPromise.class);

  public static final RuntimeException OBSOLETE_ERROR = Promise.createError("Obsolete");

  private volatile Consumer<? super T> done;
  private volatile Consumer<? super Throwable> rejected;

  protected volatile State state = State.PENDING;
  // result object or error message
  private volatile Object result;

  @NotNull
  @Override
  public State getState() {
    return state;
  }

  @NotNull
  @Override
  public Promise<T> done(@NotNull Consumer<? super T> done) {
    if (isObsolete(done)) {
      return this;
    }

    switch (state) {
      case PENDING:
        break;
      case FULFILLED:
        //noinspection unchecked
        done.consume((T)result);
        return this;
      case REJECTED:
        return this;
    }

    this.done = setHandler(this.done, done);
    return this;
  }

  @NotNull
  @Override
  public Promise<T> rejected(@NotNull Consumer<Throwable> rejected) {
    if (isObsolete(rejected)) {
      return this;
    }

    switch (state) {
      case PENDING:
        break;
      case FULFILLED:
        return this;
      case REJECTED:
        rejected.consume((Throwable)result);
        return this;
    }

    this.rejected = setHandler(this.rejected, rejected);
    return this;
  }

  @Override
  public T get() {
    //noinspection unchecked
    return state == State.FULFILLED ? (T)result : null;
  }

  @SuppressWarnings("SynchronizeOnThis")
  private static final class CompoundConsumer<T> implements Consumer<T> {
    private List<Consumer<? super T>> consumers = new ArrayList<Consumer<? super T>>();

    public CompoundConsumer(@NotNull Consumer<? super T> c1, @NotNull Consumer<? super T> c2) {
      synchronized (this) {
        consumers.add(c1);
        consumers.add(c2);
      }
    }

    @Override
    public void consume(T t) {
      List<Consumer<? super T>> list;
      synchronized (this) {
        list = consumers;
        consumers = null;
      }

      if (list != null) {
        for (Consumer<? super T> consumer : list) {
          if (!isObsolete(consumer)) {
            consumer.consume(t);
          }
        }
      }
    }

    public void add(@NotNull Consumer<? super T> consumer) {
      synchronized (this) {
        if (consumers != null) {
          consumers.add(consumer);
        }
      }
    }
  }

  @Override
  @NotNull
  public <SUB_RESULT> Promise<SUB_RESULT> then(@NotNull final Function<? super T, ? extends SUB_RESULT> fulfilled) {
    switch (state) {
      case PENDING:
        break;
      case FULFILLED:
        //noinspection unchecked
        return new DonePromise<SUB_RESULT>(fulfilled.fun((T)result));
      case REJECTED:
        return new RejectedPromise<SUB_RESULT>((Throwable)result);
    }

    final AsyncPromise<SUB_RESULT> promise = new AsyncPromise<SUB_RESULT>();
    addHandlers(new Consumer<T>() {
      @Override
      public void consume(T result) {
        try {
          if (fulfilled instanceof Obsolescent && ((Obsolescent)fulfilled).isObsolete()) {
            promise.setError(OBSOLETE_ERROR);
          }
          else {
            promise.setResult(fulfilled.fun(result));
          }
        }
        catch (Throwable e) {
          promise.setError(e);
        }
      }
    }, new Consumer<Throwable>() {
      @Override
      public void consume(Throwable error) {
        promise.setError(error);
      }
    });
    return promise;
  }

  @Override
  public void notify(@NotNull final AsyncPromise<? super T> child) {
    LOG.assertTrue(child != this);

    switch (state) {
      case PENDING:
        break;
      case FULFILLED:
        //noinspection unchecked
        child.setResult((T)result);
        return;
      case REJECTED:
        child.setError((Throwable)result);
        return;
    }

    addHandlers(new Consumer<T>() {
      @Override
      public void consume(T result) {
        try {
          child.setResult(result);
        }
        catch (Throwable e) {
          child.setError(e);
        }
      }
    }, new Consumer<Throwable>() {
      @Override
      public void consume(Throwable error) {
        child.setError(error);
      }
    });
  }

  @Override
  @NotNull
  public <SUB_RESULT> Promise<SUB_RESULT> thenAsync(@NotNull final AsyncFunction<? super T, SUB_RESULT> fulfilled) {
    switch (state) {
      case PENDING:
        break;
      case FULFILLED:
        //noinspection unchecked
        return fulfilled.fun((T)result);
      case REJECTED:
        return Promise.reject((Throwable)result);
    }

    final AsyncPromise<SUB_RESULT> promise = new AsyncPromise<SUB_RESULT>();
    final Consumer<Throwable> rejectedHandler = new Consumer<Throwable>() {
      @Override
      public void consume(Throwable error) {
        promise.setError(error);
      }
    };
    addHandlers(new Consumer<T>() {
      @Override
      public void consume(T result) {
        try {
          fulfilled.fun(result)
            .done(new Consumer<SUB_RESULT>() {
              @Override
              public void consume(SUB_RESULT result) {
                try {
                  promise.setResult(result);
                }
                catch (Throwable e) {
                  promise.setError(e);
                }
              }
            })
            .rejected(rejectedHandler);
        }
        catch (Throwable e) {
          promise.setError(e);
        }
      }
    }, rejectedHandler);
    return promise;
  }

  @Override
  @NotNull
  public Promise<T> processed(@NotNull final AsyncPromise<? super T> fulfilled) {
    switch (state) {
      case PENDING:
        break;
      case FULFILLED:
        //noinspection unchecked
        fulfilled.setResult((T)result);
        return this;
      case REJECTED:
        fulfilled.setError((Throwable)result);
        return this;
    }

    addHandlers(new Consumer<T>() {
      @Override
      public void consume(T result) {
        try {
          fulfilled.setResult(result);
        }
        catch (Throwable e) {
          fulfilled.setError(e);
        }
      }
    }, new Consumer<Throwable>() {
      @Override
      public void consume(Throwable error) {
        fulfilled.setError(error);
      }
    });
    return this;
  }

  private void addHandlers(@NotNull Consumer<T> done, @NotNull Consumer<Throwable> rejected) {
    this.done = setHandler(this.done, done);
    this.rejected = setHandler(this.rejected, rejected);
  }

  @NotNull
  private static <T> Consumer<? super T> setHandler(@Nullable Consumer<? super T> oldConsumer, @NotNull Consumer<? super T> newConsumer) {
    if (oldConsumer == null) {
      return newConsumer;
    }
    else if (oldConsumer instanceof CompoundConsumer) {
      ((CompoundConsumer<T>)oldConsumer).add(newConsumer);
      return oldConsumer;
    }
    else {
      return new CompoundConsumer<T>(oldConsumer, newConsumer);
    }
  }

  public void setResult(T result) {
    if (state != State.PENDING) {
      return;
    }

    this.result = result;
    state = State.FULFILLED;

    Consumer<? super T> done = this.done;
    clearHandlers();
    if (done != null && !isObsolete(done)) {
      done.consume(result);
    }
  }

  static boolean isObsolete(@Nullable Consumer<?> consumer) {
    return consumer instanceof Obsolescent && ((Obsolescent)consumer).isObsolete();
  }

  public boolean setError(@NotNull String error) {
    return setError(Promise.createError(error));
  }

  public boolean setError(@NotNull Throwable error) {
    if (state != State.PENDING) {
      return false;
    }

    result = error;
    state = State.REJECTED;

    Consumer<? super Throwable> rejected = this.rejected;
    clearHandlers();
    if (rejected != null) {
      if (!isObsolete(rejected)) {
        rejected.consume(error);
      }
    }
    else {
      Promise.logError(LOG, error);
    }
    return true;
  }

  private void clearHandlers() {
    done = null;
    rejected = null;
  }

  @Override
  public Promise<T> processed(@NotNull final Consumer<? super T> processed) {
    done(processed);
    rejected(new Consumer<Throwable>() {
      @Override
      public void consume(Throwable error) {
        processed.consume(null);
      }
    });
    return this;
  }
}