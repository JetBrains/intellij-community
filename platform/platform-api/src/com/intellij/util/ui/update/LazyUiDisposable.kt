// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.update;

import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.concurrent.atomic.AtomicReference;

import static com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT;
import static com.intellij.openapi.actionSystem.PlatformDataKeys.UI_DISPOSABLE;

@ApiStatus.Internal
public abstract class LazyUiDisposable<T> implements Activatable {
  private final AtomicReference<JComponent> uiRef;
  private final Disposable parent;
  private final T child;

  public LazyUiDisposable(@Nullable Disposable parent, @NotNull JComponent ui, @NotNull T child) {
    this.uiRef = new AtomicReference<>(ui);
    this.parent = parent;
    this.child = child;

    UiNotifyConnector.Once.installOn(ui, this);
  }

  @Override
  public final void showNotify() {
    JComponent ui = uiRef.getAndSet(null);
    if (ui == null) {
      return;
    }

    Project project = null;
    Disposable parent = this.parent;

    if (ApplicationManager.getApplication() != null) {
      DataContext context = DataManager.getInstance().getDataContext(ui);
      project = PROJECT.getData(context);
      if (parent == null) {
        parent = UI_DISPOSABLE.getData(context);
      }
    }
    if (parent == null) {
      if (project == null) {
        Logger.getInstance(LazyUiDisposable.class).warn("use application as a parent disposable");
        parent = ApplicationManager.getApplication();
      }
      else {
        Logger.getInstance(LazyUiDisposable.class).warn("use project as a parent disposable");
        parent = project;
      }
    }
    initialize(parent, child, project);
    if (child instanceof Disposable) {
      Disposer.register(parent, (Disposable)child);
    }
  }

  protected abstract void initialize(@NotNull Disposable parent, @NotNull T child, @Nullable Project project);
}