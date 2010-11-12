/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public interface BusyObject {

  ActionCallback getReady(Object requestor);

  public abstract static class Impl implements BusyObject {

    private final Map<Object, ActionCallback> myReadyCallbacks = new WeakHashMap<Object, ActionCallback>();

    protected abstract boolean isReady();

    public final void onReady() {
      ActionCallback[] ready = getReadyCallbacks(true);
      for (ActionCallback each : ready) {
        if (each != null) {
          each.setDone();
        }
      }
    }

    public final ActionCallback getReady(Object requestor) {
      if (isReady()) {
        return new ActionCallback.Done();
      }
      else {
        return addReadyCallback(requestor);
      }
    }

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

    private ActionCallback[] getReadyCallbacks(boolean clear) {
      synchronized (myReadyCallbacks) {
        ActionCallback[] result = myReadyCallbacks.values().toArray(new ActionCallback[myReadyCallbacks.size()]);
        if (clear) {
          myReadyCallbacks.clear();
        }
        return result;
      }
    }


    public static class Simple extends Impl {

      private AtomicInteger myBusyCount = new AtomicInteger();

      @Override
      protected boolean isReady() {
        return myBusyCount.get() == 0;
      }

      public ActionCallback execute(ActiveRunnable runnable) {
        myBusyCount.addAndGet(1);
        ActionCallback cb = runnable.run();
        cb.doWhenProcessed(new Runnable() {
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
