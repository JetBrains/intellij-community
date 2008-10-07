/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui.speedSearch;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public interface ElementFilter<T> {

  boolean shouldBeShowing(T value);

  interface Active<T> extends ElementFilter<T> {
    void fireUpdate();

    void addListener(Listener listener, Disposable parent);

    abstract class Impl<T> implements Active<T> {
      Set<Listener> myListeners = new CopyOnWriteArraySet<Listener>();

      public void fireUpdate() {
        for (final Listener myListener : myListeners) {
          myListener.update();
        }
      }

      public void addListener(final Listener listener, final Disposable parent) {
        myListeners.add(listener);
        Disposer.register(parent, new Disposable() {
          public void dispose() {
            myListeners.remove(listener);
          }
        });
      }

    }

  }

  interface Listener {
    void update();
  }

}