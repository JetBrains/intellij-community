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

import com.intellij.util.Consumer;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;

class RejectedPromise<T> extends Promise<T> {
  private final Throwable error;

  public RejectedPromise(@NotNull Throwable error) {
    this.error = error;
  }

  @NotNull
  @Override
  public Promise<T> done(@NotNull Consumer<? super T> done) {
    return this;
  }

  @NotNull
  @Override
  public Promise<T> processed(@NotNull AsyncPromise<? super T> fulfilled) {
    fulfilled.setError(error);
    return this;
  }

  @NotNull
  @Override
  public Promise<T> rejected(@NotNull Consumer<Throwable> rejected) {
    if (!AsyncPromise.isObsolete(rejected)) {
      rejected.consume(error);
    }
    return this;
  }

  @Override
  public RejectedPromise<T> processed(@NotNull Consumer<? super T> processed) {
    processed.consume(null);
    return this;
  }

  @NotNull
  @Override
  public <SUB_RESULT> Promise<SUB_RESULT> then(@NotNull Function<? super T, ? extends SUB_RESULT> done) {
    //noinspection unchecked
    return (Promise<SUB_RESULT>)this;
  }

  @NotNull
  @Override
  public <SUB_RESULT> Promise<SUB_RESULT> thenAsync(@NotNull AsyncFunction<? super T, SUB_RESULT> done) {
    //noinspection unchecked
    return (Promise<SUB_RESULT>)this;
  }

  @NotNull
  @Override
  public State getState() {
    return State.REJECTED;
  }

  @Override
  public void notify(@NotNull AsyncPromise<? super T> child) {
    child.setError(error);
  }
}