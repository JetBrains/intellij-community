// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicBoolean;

public abstract class UnknownSdkFixActionBase implements UnknownSdkFixAction {
  private final EventDispatcher<Listener> myListeners = EventDispatcher.create(Listener.class);

  @Override
  public final void addSuggestionListener(@NotNull Listener listener) {
    myListeners.addListener(listener);
  }

  @NotNull
  protected final Listener getMulticaster() {
    Listener proxy = myListeners.getMulticaster();
    return new Listener() {
      private final AtomicBoolean myIsFinished = new AtomicBoolean(false);

      @Override
      public void onSdkNameResolved(@NotNull Sdk sdk) {
        proxy.onSdkNameResolved(sdk);
      }

      @Override
      public void onSdkResolved(@NotNull Sdk sdk) {
        if (!myIsFinished.compareAndSet(false, true)) return;
        proxy.onSdkResolved(sdk);
      }

      @Override
      public void onResolveFailed() {
        if (!myIsFinished.compareAndSet(false, true)) return;
        proxy.onResolveFailed();
      }
    };
  }
}
