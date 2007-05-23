package com.intellij.openapi.wm;

import java.awt.*;

public interface WindowInfo {
  ToolWindowAnchor getAnchor();

  Rectangle getFloatingBounds();

  ToolWindowType getType();

  boolean isActive();

  boolean isAutoHide();

  boolean isDocked();

  boolean isFloating();

  boolean isSliding();
}
