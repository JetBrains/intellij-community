/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.wm;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;

import java.awt.*;

public abstract class WindowManager {
  public static WindowManager getInstance(){
    return ApplicationManager.getApplication().getComponent(WindowManager.class);
  }

  /**
   */
  public abstract void doNotSuggestAsParent(Window window);

  /**
   * Gets first window (starting from the active one) that can be parent for other windows.
   * Note, that this method returns only subclasses of dialog or frame.
   * @return <code>null</code> if there is no currently active window or there are any window
   * that can be parent.
   */
  public abstract Window suggestParentWindow(Project project);
  
  public abstract StatusBar getStatusBar(Project project);

  /**
   * Tests whether the specified rectangle is inside of screen bounds. Method uses its own heuristic test.
   * Test passes if intersection of screen bounds and specified rectangle isn't empty and its height and
   * width are not less then some value. Note, that all parameters are in screen coordinate system.
   * The method properly works in mutlimonitor configuration.
   */
  public abstract boolean isInsideScreenBounds(int x, int y, int width);

  /**
   * Tests whether the specified point is inside of screen bounds. Note, that
   * all parameters are in screen coordinate system.
   * The method properly works in mutlimonitor configuration.
   */
  public abstract boolean isInsideScreenBounds(int x,int y);
}
