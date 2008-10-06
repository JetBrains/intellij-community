package com.intellij.openapi.wm;

import java.util.EventListener;

public interface WindowManagerListener extends EventListener {
  void frameCreated(final IdeFrame frame);
  void beforeFrameReleased(final IdeFrame frame);
}
