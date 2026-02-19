// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.speedSearch;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public interface ElementFilter<T> {
  boolean shouldBeShowing(T value);

  interface Active<T> extends ElementFilter<T> {
    @NotNull
    Promise<?> fireUpdate(final @Nullable T preferredSelection, final boolean adjustSelection, final boolean now);

    void addListener(Listener<T> listener, Disposable parent);

    abstract class Impl<T> implements Active<T> {
      Set<Listener<T>> myListeners = new CopyOnWriteArraySet<>();

      @Override
      public @NotNull Promise<?> fireUpdate(@Nullable T preferredSelection, boolean adjustSelection, boolean now) {
        return Promises.all(ContainerUtil.map(myListeners, listener -> listener.update(preferredSelection, adjustSelection, now)));
      }

      @Override
      public void addListener(final Listener<T> listener, final Disposable parent) {
        myListeners.add(listener);
        Disposer.register(parent, new Disposable() {
          @Override
          public void dispose() {
            myListeners.remove(listener);
          }
        });
      }
    }
  }

  interface Listener<T> {
    @NotNull
    Promise<Void> update(final @Nullable T preferredSelection, final boolean adjustSelection, final boolean now);
  }
}