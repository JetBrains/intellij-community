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
package com.intellij.openapi.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public interface BusyObject {

  @NotNull
  ActionCallback getReady(@NotNull Object requestor);

  abstract class Impl implements BusyObject {

    private final Map<Object, ActionCallback> myReadyCallbacks = new WeakHashMap<Object, ActionCallback>();

    public abstract boolean isReady();

    public final void onReady() {
      onReady(null);
    }

    public final void onReady(@Nullable Object readyRequestor) {
      if (!isReady()) return;

      if (readyRequestor != null) {
        Pair<ActionCallback, List<ActionCallback>> callbacks = getReadyCallbacks(readyRequestor);
        callbacks.getFirst().setDone();
        for (ActionCallback each : callbacks.getSecond()) {
          each.setRejected();
        }
      } else {
        ActionCallback[] callbacks = getReadyCallbacks();
        for (ActionCallback each : callbacks) {
          each.setDone();
        }
      }

      onReadyWasSent();
    }

    protected void onReadyWasSent() {
    }

    @Override
    @NotNull
    public final ActionCallback getReady(@NotNull Object requestor) {
      if (isReady()) {
        return ActionCallback.DONE;
      }
      return addReadyCallback(requestor);
    }

    @NotNull
    private ActionCallback addReadyCallback(Object requestor) {
      synchronized (myReadyCallbacks) {
        ActionCallback cb = myReadyCallbacks.get(requestor);
        if (cb == null) {
          cb = new ActionCallback();
          myReadyCallbacks.put(requestor, cb);
        }

        return cb;
      }
    }

    @NotNull
    private ActionCallback[] getReadyCallbacks() {
      synchronized (myReadyCallbacks) {
        ActionCallback[] result = myReadyCallbacks.values().toArray(new ActionCallback[myReadyCallbacks.size()]);
        myReadyCallbacks.clear();
        return result;
      }
    }

    @NotNull
    private Pair<ActionCallback, List<ActionCallback>> getReadyCallbacks(Object readyRequestor) {
      synchronized (myReadyCallbacks) {
        ActionCallback done = myReadyCallbacks.get(readyRequestor);
        if (done == null) {
          done = new ActionCallback();
        }

        myReadyCallbacks.remove(readyRequestor);
        ArrayList<ActionCallback> rejected = new ArrayList<ActionCallback>();
        rejected.addAll(myReadyCallbacks.values());
        myReadyCallbacks.clear();
        return new Pair<ActionCallback, List<ActionCallback>>(done, rejected);
      }
    }

    public static class Simple extends Impl {

      private final AtomicInteger myBusyCount = new AtomicInteger();

      @Override
      public boolean isReady() {
        return myBusyCount.get() == 0;
      }

      @NotNull
      public ActionCallback execute(@NotNull ActiveRunnable runnable) {
        myBusyCount.addAndGet(1);
        ActionCallback cb = runnable.run();
        cb.doWhenProcessed(new Runnable() {
          @Override
          public void run() {
            myBusyCount.addAndGet(-1);
            if (isReady()) {
              onReady();
            }
          }
        });
        return cb;
      }
    }
  }

}
