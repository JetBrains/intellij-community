package com.intellij.openapi.wm.ex;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.impl.DesktopLayout;

import javax.swing.*;

public abstract class ToolWindowManagerEx extends ToolWindowManager{
  public static ToolWindowManagerEx getInstanceEx(final Project project){
    return (ToolWindowManagerEx)getInstance(project);
  }

  public abstract void addToolWindowManagerListener(ToolWindowManagerListener l);

  public abstract void removeToolWindowManagerListener(ToolWindowManagerListener l);

  /**
   * @return <code>ID</code> of tool window that was activated last time.
   */
  public abstract String getLastActiveToolWindowId();

  /**
   * @return <code>ID</code> of tool window which was last activated among tool windows satisfying the current condition
   */
  public abstract String getLastActiveToolWindowId(Condition<JComponent> condition);

  /**
   * @return layout of tool windows.
   */
  public abstract DesktopLayout getLayout();

  public abstract void setLayoutToRestoreLater(DesktopLayout layout);

  public abstract DesktopLayout getLayoutToRestoreLater();

  /**
   * Copied <code>layout</code> into internal layout and rearranges tool windows.
   */
  public abstract void setLayout(DesktopLayout layout);

  public abstract void clearSideStack();

  public abstract void hideToolWindow(String id, boolean hideSide);
}
