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
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;

import javax.swing.*;

public abstract class LazyUiDisposable<T extends Disposable> implements Activatable {

  private Throwable myAllocation;

  private boolean myWasEverShown;

  private final Disposable myParent;
  private final T myChild;

  public LazyUiDisposable(@Nullable Disposable parent, @NotNull JComponent ui, @NotNull T child) {
    if (Boolean.TRUE.toString().equalsIgnoreCase(System.getProperty("idea.is.internal"))) {
      myAllocation = new Exception();
    }

    myParent = parent;
    myChild = child;

    new UiNotifyConnector.Once(ui, this);
  }

  public final void showNotify() {
    if (myWasEverShown) return;

    try {
      findParentDisposable()
        .done(parent -> {
          Project project = null;
          if (ApplicationManager.getApplication() != null) {
            project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext());
          }
          initialize(parent, myChild, project);
          Disposer.register(parent, myChild);
        });
    }
    finally {
      myWasEverShown = true;
    }
  }

  public final void hideNotify() {
  }

  protected abstract void initialize(@NotNull Disposable parent, @NotNull T child, @Nullable Project project);

  @NotNull
  private Promise<Disposable> findParentDisposable() {
    return findDisposable(myParent, PlatformDataKeys.UI_DISPOSABLE);
  }

  private static Promise<Disposable> findDisposable(Disposable defaultValue, final DataKey<? extends Disposable> key) {
    if (defaultValue == null) {
      if (ApplicationManager.getApplication() != null) {
        final AsyncPromise<Disposable> result = new AsyncPromise<>();
        DataManager.getInstance().getDataContextFromFocus()
          .doWhenDone(new Consumer<DataContext>() {
            @Override
            public void consume(DataContext context) {
              Disposable disposable = key.getData(context);
              if (disposable == null) {
                disposable = Disposer.get("ui");
              }
              result.setResult(disposable);
            }
          });
        return result;
      }
      else {
        return null;
      }
    }
    else {
      return Promise.resolve(defaultValue);
    }
  }
}