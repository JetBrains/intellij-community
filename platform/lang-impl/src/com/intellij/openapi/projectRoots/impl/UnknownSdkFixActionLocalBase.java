// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReference;

public abstract class UnknownSdkFixActionLocalBase extends UnknownSdkFixActionBase {
  @NotNull
  protected abstract Sdk applyLocalFix();

  private Sdk runWithEvents() {
    Listener multicaster = getMulticaster();
    try {
      var sdk = applyLocalFix();
      multicaster.onSdkNameResolved(sdk);
      multicaster.onSdkResolved(sdk);
      return sdk;
    } catch (Throwable t) {
      multicaster.onResolveFailed();
      throw t;
    }
  }

  @Override
  public final void applySuggestionAsync(@Nullable Project project) {
    ApplicationManager.getApplication().invokeLater(() -> {
      try {
        runWithEvents();
      } catch (Throwable t) {
        //must be logged in the applyLocalFix
      }
    });
  }

  @NotNull
  @Override
  public final Sdk applySuggestionBlocking(@NotNull ProgressIndicator indicator) {
    var sdk = new AtomicReference<Sdk>(null);
    ApplicationManager.getApplication().invokeAndWait(() -> {
      var r = runWithEvents();
      sdk.set(r);
    });
    return sdk.get();
  }
}
