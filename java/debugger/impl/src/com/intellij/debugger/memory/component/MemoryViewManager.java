/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.debugger.memory.component;

import com.intellij.debugger.memory.event.MemoryViewManagerListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

@State(name = "MemoryViewSettings", storages = @Storage("memory.view.xml"))
public class MemoryViewManager implements ApplicationComponent, PersistentStateComponent<MemoryViewManagerState> {
  public static final String MEMORY_VIEW_CONTENT = "MemoryView";

  private final EventDispatcher<MemoryViewManagerListener> myDispatcher =
    EventDispatcher.create(MemoryViewManagerListener.class);
  private MemoryViewManagerState myState = new MemoryViewManagerState();

  public static MemoryViewManager getInstance() {
    return ApplicationManager.getApplication().getComponent(MemoryViewManager.class);
  }

  @NotNull
  @Override
  public MemoryViewManagerState getState() {
    return new MemoryViewManagerState(myState);
  }

  @Override
  public void loadState(MemoryViewManagerState state) {
    if (state == null) {
      state = new MemoryViewManagerState();
    }

    myState = state;
    fireStateChanged();
  }

  public void setShowDiffOnly(boolean value) {
    if (myState.isShowWithDiffOnly != value) {
      myState.isShowWithDiffOnly = value;
      fireStateChanged();
    }
  }

  public void setShowWithInstancesOnly(boolean value) {
    if (myState.isShowWithInstancesOnly != value) {
      myState.isShowWithInstancesOnly = value;
      fireStateChanged();
    }
  }

  public void setShowTrackedOnly(boolean value) {
    if (myState.isShowTrackedOnly != value) {
      myState.isShowTrackedOnly = value;
      fireStateChanged();
    }
  }

  public boolean isNeedShowDiffOnly() {
    return myState.isShowWithDiffOnly;
  }

  public boolean isNeedShowInstancesOnly() {
    return myState.isShowWithInstancesOnly;
  }

  public boolean isNeedShowTrackedOnly() {
    return myState.isShowTrackedOnly;
  }

  public void addMemoryViewManagerListener(MemoryViewManagerListener listener, @NotNull Disposable parentDisposable) {
    myDispatcher.addListener(listener, parentDisposable);
  }

  private void fireStateChanged() {
    myDispatcher.getMulticaster().stateChanged(new MemoryViewManagerState(myState));
  }
}
