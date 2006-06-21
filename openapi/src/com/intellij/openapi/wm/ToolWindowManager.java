/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.wm;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.Disposable;

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

  public abstract ToolWindow registerToolWindow(String id,JComponent component,ToolWindowAnchor anchor, Disposable parentDisposable);

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