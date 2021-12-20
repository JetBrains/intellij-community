// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.ui.paint.LinePainter2D;
import com.intellij.util.EarlyAccessRegistryManager;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeListener;
import java.util.function.Consumer;

@ApiStatus.Internal
public final class ToolbarUtil {
  public static void setCustomTitleBar(@NotNull Window window,
                                       @NotNull JRootPane rootPane,
                                       Consumer<? super Runnable> onDispose) {
    if (SystemInfoRt.isMac) {
      if (ExperimentalUI.isNewUI() || ExperimentalUI.isNewToolbar()) {
        setCustomTitleForToolbar(window, rootPane, onDispose);
      }
      else if (isMacTransparentTitleBarAppearance()) {
        setTransparentTitleBar(window, rootPane, onDispose);
      }
    }
  }

  private static boolean isMacTransparentTitleBarAppearance() {
    return EarlyAccessRegistryManager.INSTANCE.getBoolean("ide.mac.transparentTitleBarAppearance");
  }

  public static void setCustomTitleForToolbar(@NotNull Window window,
                                              @NotNull JRootPane rootPane,
                                              Consumer<? super Runnable> onDispose) {
    if (!SystemInfoRt.isMac || (!ExperimentalUI.isNewUI() && !ExperimentalUI.isNewToolbar())) {
      return;
    }

    JBInsets topWindowInset = JBUI.insetsTop(UIUtil.getTransparentTitleBarHeight(rootPane));
    AbstractBorder customBorder = new AbstractBorder() {
      @Override
      public Insets getBorderInsets(Component c) {
        return topWindowInset;
      }

      @Override
      public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        Graphics2D graphics = (Graphics2D)g.create();
        try {
          Rectangle headerRectangle = new Rectangle(0, 0, c.getWidth(), topWindowInset.top);
          Color color = UIManager.getColor("MainToolbar.background");
          graphics.setColor(color != null ? color : UIUtil.getPanelBackground());
          graphics.fill(headerRectangle);
        }
        finally {
          graphics.dispose();
        }
      }
    };
    rootPane.putClientProperty("apple.awt.windowTitleVisible", false);
    doSetCustomTitleBar(window, rootPane, onDispose, customBorder);
  }

  private static void doSetCustomTitleBar(@NotNull Window window,
                                          @NotNull JRootPane rootPane,
                                          Consumer<? super Runnable> onDispose,
                                          @NotNull Border customBorder) {

    rootPane.putClientProperty("apple.awt.fullWindowContent", true);
    rootPane.putClientProperty("apple.awt.transparentTitleBar", true);

    // Use standard properties starting jdk 17
    //if (Runtime.version().feature() >= 17) {
    //rootPane.putClientProperty("apple.awt.windowTitleVisible", false);
    //}

    rootPane.setBorder(customBorder);

    WindowAdapter windowAdapter = new WindowAdapter() {
      @Override
      public void windowActivated(WindowEvent e) {
        rootPane.repaint();
      }

      @Override
      public void windowDeactivated(WindowEvent e) {
        rootPane.repaint();
      }
    };
    PropertyChangeListener propertyChangeListener = e -> rootPane.repaint();
    window.addPropertyChangeListener("title", propertyChangeListener);
    onDispose.accept((Runnable)() -> {
      window.removeWindowListener(windowAdapter);
      window.removePropertyChangeListener("title", propertyChangeListener);
    });
  }

  public static void setTransparentTitleBar(@NotNull Window window,
                                            @NotNull JRootPane rootPane,
                                            Consumer<? super Runnable> onDispose) {
    if (!SystemInfoRt.isMac || !isMacTransparentTitleBarAppearance()) {
      return;
    }

    JBInsets topWindowInset = JBUI.insetsTop(UIUtil.getTransparentTitleBarHeight(rootPane));
    AbstractBorder customBorder = new AbstractBorder() {
      @Override
      public Insets getBorderInsets(Component c) {
        return topWindowInset;
      }

      @Override
      public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        Graphics2D graphics = (Graphics2D)g.create();
        try {
          Rectangle headerRectangle = new Rectangle(0, 0, c.getWidth(), topWindowInset.top);
          graphics.setColor(UIUtil.getPanelBackground());
          graphics.fill(headerRectangle);
          if (isMacTransparentTitleBarAppearance()) {
            if (window instanceof RootPaneContainer) {
              JRootPane pane = ((RootPaneContainer)window).getRootPane();
              if (pane == null || pane.getClientProperty(UIUtil.NO_BORDER_UNDER_WINDOW_TITLE_KEY) != Boolean.TRUE) {
                graphics.setColor(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground());
                LinePainter2D.paint(graphics, 0, topWindowInset.top - 1, c.getWidth(), topWindowInset.top - 1,
                                    LinePainter2D.StrokeType.INSIDE, 1);
              }
            }
          }
          Color color = window.isActive()
                        ? JBColor.black
                        : JBColor.gray;
          graphics.setColor(color);
        }
        finally {
          graphics.dispose();
        }
      }
    };
    doSetCustomTitleBar(window, rootPane, onDispose, customBorder);
  }
}
