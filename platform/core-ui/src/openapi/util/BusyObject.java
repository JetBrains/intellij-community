// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public interface BusyObject {
  @NotNull
  ActionCallback getReady(@NotNull Object requestor);

  abstract class Impl implements BusyObject {
    private final Map<Object, ActionCallback> myReadyCallbacks = new WeakHashMap<>();

    public abstract boolean isReady();

    public final void onReady() {
      onReady(null);
    }

    public final void onReady(@Nullable Object readyRequestor) {
      if (!isReady()) {
        return;
      }

      if (readyRequestor == null) {
        for (ActionCallback each : getReadyCallbacks()) {
          each.setDone();
        }
      }
      else {
        Pair<ActionCallback, List<ActionCallback>> callbacks = getReadyCallbacks(readyRequestor);
        callbacks.getFirst().setDone();
        for (ActionCallback each : callbacks.getSecond()) {
          each.setRejected();
        }
      }

      onReadyWasSent();
    }

    protected void onReadyWasSent() {
    }

    @Override
    public final @NotNull ActionCallback getReady(@NotNull Object requestor) {
      return isReady() ? ActionCallback.DONE : addReadyCallback(requestor);
    }

    private @NotNull ActionCallback addReadyCallback(Object requestor) {
      synchronized (myReadyCallbacks) {
        ActionCallback cb = myReadyCallbacks.get(requestor);
        if (cb == null) {
          cb = new ActionCallback();
          myReadyCallbacks.put(requestor, cb);
        }

        return cb;
      }
    }

    private ActionCallback @NotNull [] getReadyCallbacks() {
      synchronized (myReadyCallbacks) {
        ActionCallback[] result = myReadyCallbacks.values().toArray(new ActionCallback[0]);
        myReadyCallbacks.clear();
        return result;
      }
    }

    private @NotNull Pair<ActionCallback, List<ActionCallback>> getReadyCallbacks(Object readyRequestor) {
      synchronized (myReadyCallbacks) {
        ActionCallback done = myReadyCallbacks.get(readyRequestor);
        if (done == null) {
          done = new ActionCallback();
        }

        myReadyCallbacks.remove(readyRequestor);
        ArrayList<ActionCallback> rejected = new ArrayList<>(myReadyCallbacks.values());
        myReadyCallbacks.clear();
        return new Pair<>(done, rejected);
      }
    }
  }
}
