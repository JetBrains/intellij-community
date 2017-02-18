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
package com.intellij.util.ui.update;

import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import java.util.concurrent.atomic.AtomicReference;

import static com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT;
import static com.intellij.openapi.actionSystem.PlatformDataKeys.UI_DISPOSABLE;

public abstract class LazyUiDisposable<T extends Disposable> implements Activatable {

  private Throwable myAllocation;

  private final AtomicReference<JComponent> myUI;
  private final Disposable myParent;
  private final T myChild;

  public LazyUiDisposable(@Nullable Disposable parent, @NotNull JComponent ui, @NotNull T child) {
    if (Boolean.TRUE.toString().equalsIgnoreCase(System.getProperty("idea.is.internal"))) {
      myAllocation = new Exception();
    }

    myUI = new AtomicReference<>(ui);
    myParent = parent;
    myChild = child;

    new UiNotifyConnector.Once(ui, this);
  }

  public final void showNotify() {
    JComponent ui = myUI.getAndSet(null);
    if (ui == null) return;

    Project project = null;
    Disposable parent = myParent;

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
        parent = Disposer.get("ui");
      }
      else {
        Logger.getInstance(LazyUiDisposable.class).warn("use project as a parent disposable");
        parent = project;
      }
    }
    initialize(parent, myChild, project);
    Disposer.register(parent, myChild);
  }

  public final void hideNotify() {
  }

  protected abstract void initialize(@NotNull Disposable parent, @NotNull T child, @Nullable Project project);
}