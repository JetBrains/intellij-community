// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.BusyObject;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class FrameStateManagerImpl extends FrameStateManager {
  private final List<FrameStateListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  private final BusyObject.Impl myActive;

  public FrameStateManagerImpl() {
    myActive = new BusyObject.Impl() {
      @Override
      public boolean isReady() {
        return ApplicationManager.getApplication().isActive();
      }
    };

    ApplicationManager.getApplication().getMessageBus().connect().subscribe(ApplicationActivationListener.TOPIC, new ApplicationActivationListener() {
      @Override
      public void applicationActivated(@NotNull IdeFrame ideFrame) {
        System.setProperty("com.jetbrains.suppressWindowRaise", "false");
        myActive.onReady();
        fireActivationEvent();
      }

      @Override
      public void applicationDeactivated(@NotNull IdeFrame ideFrame) {
        System.setProperty("com.jetbrains.suppressWindowRaise", "true");
        if (!ApplicationManager.getApplication().isDisposed()) {
          fireDeactivationEvent();
        }
      }
    });
  }

  @Override
  public ActionCallback getApplicationActive() {
    return myActive.getReady(this);
  }

  private void fireDeactivationEvent() {
    for (FrameStateListener listener : myListeners) {
      listener.onFrameDeactivated();
    }
  }

  private void fireActivationEvent() {
    for (FrameStateListener listener : myListeners) {
      listener.onFrameActivated();
    }
  }

  @Override
  public void addListener(@NotNull FrameStateListener listener) {
    myListeners.add(listener);
  }

  @Override
  public void addListener(@NotNull final FrameStateListener listener, @Nullable Disposable disposable) {
    if (disposable != null) {
      ApplicationManager.getApplication().getMessageBus().connect(disposable).subscribe(FrameStateListener.TOPIC, listener);
    }
    else {
      myListeners.add(listener);
    }
  }

  @Override
  public void removeListener(@NotNull FrameStateListener listener) {
    myListeners.remove(listener);
  }
}
