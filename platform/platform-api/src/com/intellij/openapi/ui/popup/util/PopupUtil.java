/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.ui.popup.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.basic.ComboPopup;
import java.awt.*;
import java.awt.event.ComponentEvent;
import java.lang.reflect.Method;

public class PopupUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.ui.popup.util.PopupUtil");

  private PopupUtil() {
  }

  @Nullable
  public static Component getOwner(@Nullable Component c) {
    if (c == null) return null;

    final Window wnd = SwingUtilities.getWindowAncestor(c);
    if (wnd instanceof JWindow) {
      final JRootPane root = ((JWindow)wnd).getRootPane();
      final JBPopup popup = (JBPopup)root.getClientProperty(JBPopup.KEY);
      if (popup == null) return c;

      final Component owner = popup.getOwner();
      if (owner == null) return c;

      return getOwner(owner);
    }
    else {
      return c;
    }
  }

  public static JBPopup getPopupContainerFor(@Nullable Component c) {
    if (c == null) return null;

    final Window wnd = SwingUtilities.getWindowAncestor(c);
    if (wnd instanceof JWindow) {
      final JRootPane root = ((JWindow)wnd).getRootPane();
      return (JBPopup)root.getClientProperty(JBPopup.KEY);
    }

    return null;

  }

  public static void setPopupType(@NotNull final PopupFactory factory, final int type) {
    try {
      final Method method = PopupFactory.class.getDeclaredMethod("setPopupType", int.class);
      method.setAccessible(true);
      method.invoke(factory, type);
    }
    catch (Throwable e) {
      LOG.error(e);
    }
  }

  public static int getPopupType(@NotNull final PopupFactory factory) {
    try {
      final Method method = PopupFactory.class.getDeclaredMethod("getPopupType");
      method.setAccessible(true);
      final Object result = method.invoke(factory);
      return result instanceof Integer ? (Integer) result : -1;
    }
    catch (Throwable e) {
      LOG.error(e);
    }

    return -1;
  }

  public static Component getActiveComponent() {
    Window[] windows = Window.getWindows();
    for (Window each : windows) {
      if (each.isActive()) {
        return each;
      }
    }

    final IdeFrame frame = IdeFocusManager.findInstance().getLastFocusedFrame();
    if (frame != null) return frame.getComponent();
    return JOptionPane.getRootFrame();
  }

  public static void showBalloonForActiveFrame(@NotNull final String message, final MessageType type) {
    final Runnable runnable = new Runnable() {
      public void run() {
        final IdeFrame frame = IdeFocusManager.findInstance().getLastFocusedFrame();
        if (frame == null) {
          final Project[] projects = ProjectManager.getInstance().getOpenProjects();
          final Project project = projects == null || projects.length == 0 ? ProjectManager.getInstance().getDefaultProject() : projects[0];
          final JFrame jFrame = WindowManager.getInstance().getFrame(project);
          if (jFrame != null) {
            showBalloonForComponent(jFrame, message, type, true, project);
          } else {
            LOG.info("Can not get component to show message: " + message);
          }
          return;
        }
        showBalloonForComponent(frame.getComponent(), message, type, true, frame.getProject());
      }
    };
    UIUtil.invokeLaterIfNeeded(runnable);
  }

  public static void showBalloonForActiveComponent(@NotNull final String message, final MessageType type) {
    Runnable runnable = new Runnable() {
      public void run() {
        Window[] windows = Window.getWindows();
        Window targetWindow = null;
        for (Window each : windows) {
          if (each.isActive()) {
            targetWindow = each;
            break;
          }
        }

        if (targetWindow == null) {
          targetWindow = JOptionPane.getRootFrame();
        }

        if (targetWindow == null) {
          final IdeFrame frame = IdeFocusManager.findInstance().getLastFocusedFrame();
          if (frame == null) {
            final Project[] projects = ProjectManager.getInstance().getOpenProjects();
            final Project project = projects == null || projects.length == 0 ? ProjectManager.getInstance().getDefaultProject() : projects[0];
            final JFrame jFrame = WindowManager.getInstance().getFrame(project);
            if (jFrame != null) {
              showBalloonForComponent(jFrame, message, type, true, project);
            } else {
              LOG.info("Can not get component to show message: " + message);
            }
            return;
          }
          showBalloonForComponent(frame.getComponent(), message, type, true, frame.getProject());
        } else {
          showBalloonForComponent(targetWindow, message, type, true, null);
        }
      }
    };
    UIUtil.invokeLaterIfNeeded(runnable);
  }

  public static void showBalloonForComponent(@NotNull Component component, @NotNull final String message, final MessageType type,
                                             final boolean atTop, @Nullable final Disposable disposable) {
    final JBPopupFactory popupFactory = JBPopupFactory.getInstance();
    if (popupFactory == null) return;
    BalloonBuilder balloonBuilder = popupFactory.createHtmlTextBalloonBuilder(message, type, null);
    balloonBuilder.setDisposable(disposable == null ? ApplicationManager.getApplication() : disposable);
    Balloon balloon = balloonBuilder.createBalloon();
    Dimension size = component.getSize();
    Balloon.Position position;
    int x;
    int y;
    if (size == null) {
      x = y = 0;
      position = Balloon.Position.above;
    }
    else {
      x = Math.min(10, size.width / 2);
      y = size.height;
      position = Balloon.Position.below;
    }
    balloon.show(new RelativePoint(component, new Point(x, y)), position);
  }

  public static boolean isComboPopupKeyEvent(@NotNull ComponentEvent event, @NotNull JComboBox comboBox) {
    final Component component = event.getComponent();
    if(!comboBox.isPopupVisible() || component == null) return false;
    ComboPopup popup = ReflectionUtil.getField(comboBox.getUI().getClass(), comboBox.getUI(), ComboPopup.class, "popup");
    return popup != null && SwingUtilities.isDescendingFrom(popup.getList(), component);
  }

  public static boolean handleEscKeyEvent() {
    MenuSelectionManager menuSelectionManager = MenuSelectionManager.defaultManager();
    MenuElement[] selectedPath = menuSelectionManager.getSelectedPath();
    if (selectedPath.length > 0) { // hide popup menu if any
      menuSelectionManager.clearSelectedPath();
      return true;
    }
    else {
      if (ApplicationManager.getApplication() == null) {
        return false;
      }
      final StackingPopupDispatcher popupDispatcher = StackingPopupDispatcher.getInstance();
      if (popupDispatcher != null && !popupDispatcher.isPopupFocused()) {
        return false;
      }
      return true;
    }
  }
}