// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Special editor-related wrapper for {@link CompletableFuture}.
 *
 * <p>A plain {@link CompletableFuture} drops an exception which a {@code thenAccept} callback throws:
 * this class logs such an exception instead, and then rethrows it
 */
@ApiStatus.Internal
public class EditorOpenFuture<T> extends CompletableFuture<T> {
  private static final Logger LOG = Logger.getInstance(EditorOpenFuture.class);

  /** @return a future which already holds {@code value} and still guards its callbacks. */
  public static <T> @NotNull CompletableFuture<T> completed(T value) {
    EditorOpenFuture<T> future = new EditorOpenFuture<>();
    future.complete(value);
    return future;
  }

  @Override
  public <U> @NotNull CompletableFuture<U> newIncompleteFuture() {
    return new EditorOpenFuture<>();
  }

  @Override
  public @NotNull CompletableFuture<Void> thenAccept(@NotNull Consumer<? super T> action) {
    return super.thenAccept(guarded(action));
  }

  @Override
  public @NotNull CompletableFuture<Void> thenAcceptAsync(@NotNull Consumer<? super T> action) {
    return super.thenAcceptAsync(guarded(action), defaultExecutor());
  }

  @Override
  public @NotNull CompletableFuture<Void> thenAcceptAsync(@NotNull Consumer<? super T> action, @NotNull Executor executor) {
    return super.thenAcceptAsync(guarded(action), executor);
  }

  private static <T> @NotNull Consumer<T> guarded(@NotNull Consumer<T> action) {
    return value -> {
      try {
        action.accept(value);
      }
      catch (CancellationException e) {
        throw e;
      }
      catch (Throwable e) {
        LOG.error(e);
        throw e;
      }
    };
  }
}
