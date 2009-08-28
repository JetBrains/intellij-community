/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui.speedSearch;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ActionCallback;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public interface ElementFilter<T> {

  ElementFilter PASS_THROUGH = new ElementFilter() {
    public boolean shouldBeShowing(Object value) {
      return true;
    }
  };

  boolean shouldBeShowing(T value);

  interface Active<T> extends ElementFilter<T> {
    ActionCallback fireUpdate(@Nullable final T preferredSelection, final boolean adjustSelection, final boolean now);

    void addListener(Listener<T> listener, Disposable parent);

    abstract class Impl<T> implements Active<T> {
      Set<Listener<T>> myListeners = new CopyOnWriteArraySet<Listener<T>>();

      public ActionCallback fireUpdate(final T preferredSelection, final boolean adjustSelection, final boolean now) {
        final ActionCallback result = new ActionCallback(myListeners.size());
        for (final Listener<T> myListener : myListeners) {
          myListener.update(preferredSelection, adjustSelection, now).doWhenProcessed(new Runnable() {
            public void run() {
              result.setDone();
            }
          });
        }

        return result;
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
    ActionCallback update(@Nullable final T preferredSelection, final boolean adjustSelection, final boolean now);
  }

}