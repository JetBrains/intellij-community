// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.progress.ProcessCanceledException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

// separate class to keep ExtensionPointImpl class implementation clear and readable,
// such simple util code better to keep separately.
@ApiStatus.Internal
public final class ExtensionProcessingHelper {
  public static <T> void forEachExtensionSafe(@NotNull Consumer<? super T> extensionConsumer, @NotNull Iterable<T> iterable) {
    for (T t : iterable) {
      if (t == null) break;
      try {
        extensionConsumer.accept(t);
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Exception e) {
        ExtensionPointImpl.LOG.error(e);
      }
    }
  }

  @Nullable
  public static <T> T findFirstSafe(@NotNull Predicate<? super T> predicate, @NotNull Iterable<T> iterable) {
    return computeSafeIfAny(o -> predicate.test(o) ? o : null, iterable);
  }

  @Nullable
  public static <T, R> R computeSafeIfAny(@NotNull Function<T, R> processor, @NotNull Iterable<T> iterable) {
    for (T t : iterable) {
      if (t == null) {
        return null;
      }

      try {
        R result = processor.apply(t);
        if (result != null) {
          return result;
        }
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Exception e) {
        ExtensionPointImpl.LOG.error(e);
      }
    }

    return null;
  }
}
