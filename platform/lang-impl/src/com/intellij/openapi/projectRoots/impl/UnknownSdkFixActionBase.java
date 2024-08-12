// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicBoolean;

public abstract class UnknownSdkFixActionBase implements UnknownSdkFixAction, FixWithConsent {
  private final EventDispatcher<Listener> myListeners = EventDispatcher.create(Listener.class);
  private boolean hasUserConsent = false;

  @Override
  public final void addSuggestionListener(@NotNull Listener listener) {
    myListeners.addListener(listener);
  }

  protected final @NotNull Listener getMulticaster() {
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

      @Override
      public void onResolveCancelled() {
        if (!myIsFinished.compareAndSet(false, true)) return;
        proxy.onResolveCancelled();
      }
    };
  }

  @Override
  public void giveConsent() { hasUserConsent = true; }

  @Override
  public boolean hasConsent() { return hasUserConsent; }
}
