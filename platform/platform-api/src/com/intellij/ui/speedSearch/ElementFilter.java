/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
      Set<Listener<T>> myListeners = new CopyOnWriteArraySet<>();

      public ActionCallback fireUpdate(@Nullable final T preferredSelection, final boolean adjustSelection, final boolean now) {
        final ActionCallback result = new ActionCallback(myListeners.size());
        for (final Listener<T> myListener : myListeners) {
          myListener.update(preferredSelection, adjustSelection, now).doWhenProcessed(result.createSetDoneRunnable());
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