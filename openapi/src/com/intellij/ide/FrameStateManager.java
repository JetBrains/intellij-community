package com.intellij.ide;

import com.intellij.openapi.application.ApplicationManager;

public abstract class FrameStateManager {
  public static FrameStateManager getInstance() {
    return ApplicationManager.getApplication().getComponent(FrameStateManager.class);
  }

  public abstract void addListener(FrameStateListener listener);
  public abstract void removeListener(FrameStateListener listener);
}
