/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui.speedSearch;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public interface ElementFilter<T> {

  boolean shouldBeShowing(T value);

  interface Active<T> extends ElementFilter<T> {
    void fireUpdate(@Nullable final T preferredSelection);

    void addListener(Listener<T> listener, Disposable parent);

    abstract class Impl<T> implements Active<T> {
      Set<Listener<T>> myListeners = new CopyOnWriteArraySet<Listener<T>>();

      public void fireUpdate(final T preferredSelection) {
        for (final Listener<T> myListener : myListeners) {
          myListener.update(preferredSelection);
        }
      }

      public void addListener(final Listener<T> listener, final Disposable parent) {
        myListeners.add(listener);
        Disposer.register(parent, new Disposable() {
          public void dispose() {
            myListeners.remove(listener);
          }
        });
      }

    }

  }

  interface Listener<T> {
    void update(@Nullable final T preferredSelection);
  }

}