// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.impl;

import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerContextListener;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.DebuggerStateManager;
import com.intellij.debugger.ui.DebuggerView;
import com.intellij.openapi.CompositeDisposable;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public abstract class UpdatableDebuggerView extends JPanel implements DebuggerView {
  private final Project myProject;
  private final DebuggerStateManager myStateManager;
  private volatile boolean myRefreshNeeded = true;
  private final CompositeDisposable myDisposables = new CompositeDisposable();
  private volatile boolean myUpdateEnabled;

  protected UpdatableDebuggerView(final Project project, final DebuggerStateManager stateManager) {
    setLayout(new BorderLayout());
    myProject = project;
    myStateManager = stateManager;

    final DebuggerContextListener contextListener = this::changeEvent;
    myStateManager.addListener(contextListener);

    registerDisposable(new Disposable() {
      @Override
      public void dispose() {
        myStateManager.removeListener(contextListener);
      }
    });
  }

  protected void changeEvent(final DebuggerContextImpl newContext, final DebuggerSession.Event event) {
    if (newContext.getDebuggerSession() != null) {
      rebuildIfVisible(event);
    }
  }

  protected final boolean isUpdateEnabled() {
    return myUpdateEnabled || isShowing();
  }

  @Override
  public final void setUpdateEnabled(final boolean enabled) {
    myUpdateEnabled = enabled;
  }

  @Override
  public final boolean isRefreshNeeded() {
    return myRefreshNeeded;
  }

  @Override
  public final void rebuildIfVisible(final DebuggerSession.Event event) {
    if (isUpdateEnabled()) {
      myRefreshNeeded = false;
      rebuild(event);
    }
    else {
      myRefreshNeeded = true;
    }
  }

  protected abstract void rebuild(DebuggerSession.Event event);

  protected final void registerDisposable(Disposable disposable) {
    myDisposables.add(disposable);
  }

  public @NotNull DebuggerContextImpl getContext() {
    return myStateManager.getContext();
  }

  protected final Project getProject() {
    return myProject;
  }

  public DebuggerStateManager getContextManager() {
    return myStateManager;
  }

  public void dispose() {
    Disposer.dispose(myDisposables);
  }
}
