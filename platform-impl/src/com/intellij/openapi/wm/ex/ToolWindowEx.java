package com.intellij.openapi.wm.ex;

import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowType;
import org.jetbrains.annotations.NonNls;

import java.beans.PropertyChangeListener;

public interface ToolWindowEx extends ToolWindow {
  @NonNls String PROP_AVAILABLE = "available";
  @NonNls String PROP_ICON = "icon";
  @NonNls String PROP_TITLE = "title";

  /**
   * Removes specified property change listener.
   *
   * @param l listener to be removed.
   */
  void removePropertyChangeListener(PropertyChangeListener l);

  /**
   * @return type of internal decoration of tool window.
   * @throws IllegalStateException
   *          if tool window isn't installed.
   */
  ToolWindowType getInternalType();

  void ensureContentInitialized();
}
