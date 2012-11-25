/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.ide;

import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.BusyObject;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.util.Alarm;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class FrameStateManagerImpl extends FrameStateManager implements ApplicationComponent {
  private final List<FrameStateListener> myListeners = ContainerUtil.createEmptyCOWList();

  private boolean myShouldSynchronize;
  private final Alarm mySyncAlarm;

  private final BusyObject.Impl myActive;
  private final ApplicationImpl myApp;

  public FrameStateManagerImpl(final ApplicationImpl app) {
    myApp = app;
    myActive = new BusyObject.Impl() {
      @Override
      public boolean isReady() {
        return myApp.isActive();
      }
    };


    myShouldSynchronize = false;
    mySyncAlarm = new Alarm();

    app.getMessageBus().connect().subscribe(ApplicationActivationListener.TOPIC, new ApplicationActivationListener() {
      @Override
      public void applicationActivated(IdeFrame ideFrame) {
        myActive.onReady();
        mySyncAlarm.cancelAllRequests();
        if (myShouldSynchronize) {
          myShouldSynchronize = false;
          fireActivationEvent();
        }
      }

      @Override
      public void applicationDeactivated(IdeFrame ideFrame) {
        mySyncAlarm.cancelAllRequests();
        mySyncAlarm.addRequest(new Runnable() {
          public void run() {
            if (!app.isActive()) {
              myShouldSynchronize = true;
              fireDeactivationEvent();
            }
          }
        }, 200);
      }
    });

  }

  @Override
  public ActionCallback getApplicationActive() {
    return myActive.getReady(this);
  }

  @NotNull
  @NonNls
  public String getComponentName() {
    return "FrameStateManager";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
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

  public synchronized void addListener(FrameStateListener listener) {
    myListeners.add(listener);
  }

  public synchronized void removeListener(FrameStateListener listener) {
    myListeners.remove(listener);
  }

}
