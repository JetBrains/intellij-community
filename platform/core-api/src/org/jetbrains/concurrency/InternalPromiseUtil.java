// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.concurrency;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Only internal usage.
 */
public class InternalPromiseUtil {
  //public static NotNullLazyValue<Promise<Object>> CANCELLED_PROMISE = new NotNullLazyValue<>() {
  //  @NotNull
  //  @Override
  //  protected Object compute() {
  //    return null;
  //  }
  //}

  public static boolean isHandlerObsolete(@NotNull Object handler) {
    return handler instanceof Obsolescent && ((Obsolescent)handler).isObsolete();
  }

  public static class PromiseValue<T> {
    public final T result;
    public final Throwable error;

    public static <T> PromiseValue<T> createFulfilled(@Nullable T result) {
      return new PromiseValue<>(result, null);
    }

    public static <T> PromiseValue<T> createRejected(@Nullable Throwable error) {
      return new PromiseValue<>(null, error);
    }

    private PromiseValue(@Nullable T result, @Nullable Throwable error) {
      this.result = result;
      this.error = error;
    }

    @NotNull
    public Promise.State getState() {
      return error == null ? Promise.State.FULFILLED : Promise.State.REJECTED;
    }
  }
}
