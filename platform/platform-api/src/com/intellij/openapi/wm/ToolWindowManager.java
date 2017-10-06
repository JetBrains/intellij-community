/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.wm;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkListener;
import java.util.Objects;

/**
 * If you want to register a toolwindow, which will be enabled during the dumb mode, please use {@link ToolWindowManager}'s
 * registration methods which have 'canWorkInDumbMode' parameter.
 */
public abstract class ToolWindowManager {

  public abstract boolean canShowNotification(@NotNull String toolWindowId);

  public static ToolWindowManager getInstance(@NotNull Project project){
    return project.getComponent(ToolWindowManager.class);
  }

  /**
   * Register specified tool window into IDE window system.
   * @param id {@code id} of tool window to be registered.
   * @param component {@code component} which represents tool window content.
   * May be null. Content can be further added via content manager for this tool window (See {@link ToolWindow#getContentManager()})
   * @param anchor the default anchor for first registration. It uses only first time the
   * tool window with the specified {@code id} is being registered into the window system.
   * After the first registration window's anchor is stored in project file
   * and {@code anchor} is ignored.
   * @exception IllegalArgumentException if the same window is already installed or one
   * of the parameters is {@code null}.
   * @return tool window
   * @deprecated  {@link ToolWindowManager#registerToolWindow(String, boolean, ToolWindowAnchor)}
   */
  @Deprecated
  @NotNull
  public abstract ToolWindow registerToolWindow(@NotNull String id, @NotNull JComponent component, @NotNull ToolWindowAnchor anchor);

  /**
  * @deprecated  {@link ToolWindowManager#registerToolWindow(String, boolean, ToolWindowAnchor)}
  */
  @Deprecated
  @NotNull
  public abstract ToolWindow registerToolWindow(@NotNull String id,
                                                @NotNull JComponent component,
                                                @NotNull ToolWindowAnchor anchor,
                                                @NotNull Disposable parentDisposable);

  /**
  * @deprecated  {@link ToolWindowManager#registerToolWindow(String, boolean, ToolWindowAnchor)}
  */
  @Deprecated
  @NotNull
  public abstract ToolWindow registerToolWindow(@NotNull String id,
                                                @NotNull JComponent component,
                                                @NotNull ToolWindowAnchor anchor,
                                                @NotNull Disposable parentDisposable,
                                                boolean canWorkInDumbMode);
  /**
  * @deprecated  {@link ToolWindowManager#registerToolWindow(String, boolean, ToolWindowAnchor)}
  */
  @Deprecated
  @NotNull
  public abstract ToolWindow registerToolWindow(@NotNull String id,
                                                @NotNull JComponent component,
                                                @NotNull ToolWindowAnchor anchor,
                                                @NotNull Disposable parentDisposable,
                                                boolean canWorkInDumbMode,
                                                boolean canCloseContents);

  @NotNull
  public abstract ToolWindow registerToolWindow(@NotNull String id, boolean canCloseContent, @NotNull ToolWindowAnchor anchor);

  @NotNull
  public abstract ToolWindow registerToolWindow(@NotNull String id,
                                                boolean canCloseContent,
                                                @NotNull ToolWindowAnchor anchor,
                                                boolean secondary);

  @NotNull
  public abstract ToolWindow registerToolWindow(@NotNull String id,
                                                boolean canCloseContent,
                                                @NotNull ToolWindowAnchor anchor,
                                                @NotNull Disposable parentDisposable,
                                                boolean canWorkInDumbMode);

  @NotNull
  public abstract ToolWindow registerToolWindow(@NotNull String id,
                                                boolean canCloseContent,
                                                @NotNull ToolWindowAnchor anchor,
                                                @NotNull Disposable parentDisposable,
                                                boolean canWorkInDumbMode,
                                                boolean secondary);

  @NotNull
  public ToolWindow registerToolWindow(@NotNull final String id,
                                       final boolean canCloseContent,
                                       @NotNull final ToolWindowAnchor anchor,
                                       @NotNull Disposable parentDisposable) {
    return registerToolWindow(id, canCloseContent, anchor, parentDisposable, false);
  }

  /**
   * does nothing if tool window with specified isn't registered.
   */
  public abstract void unregisterToolWindow(@NotNull String id);

  /**
   */
  public abstract void activateEditorComponent();

  /**
   * @return {@code true} if and only if editor component is active.
   */
  public abstract boolean isEditorComponentActive();

  /**
   * @return array of {@code id}s of all registered tool windows.
   */
  @NotNull
  public abstract String[] getToolWindowIds();

  /**
   * @return {@code ID} of currently active tool window or {@code null} if there is no active
   * tool window.
   */
  @Nullable
  public abstract String getActiveToolWindowId();

  @Nullable
  public static ToolWindow getActiveToolWindow () {
    IdeFrame frame = IdeFocusManager.getGlobalInstance().getLastFocusedFrame();
    Project project = frame == null ? ProjectManager.getInstance().getDefaultProject() : frame.getProject();

    if (project != null) {
      ToolWindowManager managerInstance = getInstance(project);
      if (managerInstance != null) {
        return managerInstance.getToolWindow(getActiveId());
      }
    }

    return null;
  }

  @Nullable
  public static String getActiveId () {
    IdeFrame lastFocusedFrame = IdeFocusManager.getGlobalInstance().getLastFocusedFrame();

    if (lastFocusedFrame !=null) {
      Project project = lastFocusedFrame.getProject();
      if (project != null) {
        ToolWindowManager instance = getInstance(project);
        return instance == null ? "" : instance.getActiveToolWindowId();
      }
    }
    return "";
  }


  /**
   * @return registered tool window with specified {@code id}. If there is no registered
   * tool window with specified {@code id} then the method returns {@code null}.
   */
  public abstract ToolWindow getToolWindow(@Nullable String id);

  /**
   * Puts specified runnable to the tail of current command queue.
   */
  public abstract void invokeLater(@NotNull Runnable runnable);

  /**
   * Utility method for quick access to the focus manager
   */
  @NotNull
  public abstract IdeFocusManager getFocusManager();

  public abstract void notifyByBalloon(@NotNull final String toolWindowId, @NotNull final MessageType type, @NotNull final String htmlBody);

  public abstract void notifyByBalloon(@NotNull final String toolWindowId,
                                       @NotNull final MessageType type,
                                       @NotNull final String htmlBody,
                                       @Nullable final Icon icon,
                                       @Nullable HyperlinkListener listener);

  @Nullable
  public abstract Balloon getToolWindowBalloon(String id);

  public abstract boolean isMaximized(@NotNull ToolWindow wnd);

  public abstract void setMaximized(@NotNull ToolWindow wnd, boolean maximized);
}
