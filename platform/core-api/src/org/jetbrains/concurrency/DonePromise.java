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

import com.intellij.openapi.util.Getter;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;

class DonePromise<T> extends Promise<T> implements Getter<T> {
  private final T result;

  public DonePromise(T result) {
    this.result = result;
  }

  @NotNull
  @Override
  public Promise<T> done(@NotNull Consumer<? super T> done) {
    if (!AsyncPromise.isObsolete(done)) {
      done.consume(result);
    }
    return this;
  }

  @NotNull
  @Override
  public Promise<T> processed(@NotNull AsyncPromise<? super T> fulfilled) {
    fulfilled.setResult(result);
    return this;
  }

  @Override
  public Promise<T> processed(@NotNull Consumer<? super T> processed) {
    done(processed);
    return this;
  }

  @NotNull
  @Override
  public Promise<T> rejected(@NotNull Consumer<Throwable> rejected) {
    return this;
  }

  @NotNull
  @Override
  public <SUB_RESULT> Promise<SUB_RESULT> then(@NotNull Function<? super T, ? extends SUB_RESULT> done) {
    if (done instanceof Obsolescent && ((Obsolescent)done).isObsolete()) {
      return Promise.reject("obsolete");
    }
    else {
      return Promise.resolve(done.fun(result));
    }
  }

  @NotNull
  @Override
  public <SUB_RESULT> Promise<SUB_RESULT> thenAsync(@NotNull AsyncFunction<? super T, SUB_RESULT> done) {
    return done.fun(result);
  }

  @NotNull
  @Override
  public State getState() {
    return State.FULFILLED;
  }

  @Override
  public T get() {
    return result;
  }

  @Override
  public void notify(@NotNull AsyncPromise<? super T> child) {
    child.setResult(result);
  }
}