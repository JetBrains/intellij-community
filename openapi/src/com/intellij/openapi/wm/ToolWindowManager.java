/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.wm;

import com.intellij.openapi.project.Project;

import javax.swing.*;

public abstract class ToolWindowManager {

  public static ToolWindowManager getInstance(Project project){
    return project.getComponent(ToolWindowManager.class);
  }

  /**
   * Register specified tool window into IDE window system.
   * @param id <code>id</code> of tool window to be registered.
   * @param component <code>component</code> which represents tool window content.
   * @param anchor the default anchor for first registration. It uses only first time the
   * tool window with the specified <code>id</code> is being registered into the window system.
   * After the first registration window's anchor is stored in project file
   * and <code>anchor</code> is ignored.
   * @exception java.lang.IllegalArgumentException if the same window is already installed or one
   * of the parameters is <code>null</code>.
   */
  public abstract ToolWindow registerToolWindow(String id,JComponent component,ToolWindowAnchor anchor);

  /**
   * @exception java.lang.IllegalArgumentException if tool window with specified isn't
   * registered.
   */
  public abstract void unregisterToolWindow(String id);

  /**
   */
  public abstract void activateEditorComponent();

  /**
   * @return <code>true</code> if and only if editor component is active.
   */
  public abstract boolean isEditorComponentActive();

  /**
   * @return array of <code>id</code>s of all registered tool windows.
   */
  public abstract String[] getToolWindowIds();

  /**
   * @return <code>ID</code> of currently active tool window or <code>null</code> if there is no active
   * tool window.
   */
  public abstract String getActiveToolWindowId();

  /**
   * @return registered tool window with specified <code>id</code>. If there is no registered
   * tool window with specified <code>id</code> then the method returns <code>null</code>.
   */
  public abstract ToolWindow getToolWindow(String id);

  /**
   * Puts specified runnable to the tail of current command queue.
   */
  public abstract void invokeLater(Runnable runnable);
}