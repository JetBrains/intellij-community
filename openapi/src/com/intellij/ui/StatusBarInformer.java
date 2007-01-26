package com.intellij.ui;

import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.IdeFrame;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.*;

import org.jetbrains.annotations.Nullable;

public class StatusBarInformer {

  private String myText;
  private StatusBar myStatusBar;
  private JComponent myComponent;

  public StatusBarInformer(final JComponent component, String text) {
    this(component, text, null);
  }

  public StatusBarInformer(final JComponent component, String text, StatusBar statusBar) {
    myText = text;
    myStatusBar = statusBar;
    myComponent = component;
    myComponent.addMouseListener(new MouseAdapter() {
      public void mouseEntered(final MouseEvent e) {
        final StatusBar bar = getStatusBar();
        final String text = getText();
        if (bar != null) {
          bar.setInfo(text);
        }
        myComponent.setToolTipText(text);
      }

      public void mouseExited(final MouseEvent e) {
        final StatusBar bar = getStatusBar();
        if (bar != null) {
          bar.setInfo(null);
        }
        myComponent.setToolTipText(null);
      }
    });
  }

  @Nullable
  protected String getText() {
    return myText;
  }

  @Nullable
  protected StatusBar getStatusBar() {
    if (myStatusBar != null) return myStatusBar;
    final Window window = SwingUtilities.getWindowAncestor(myComponent);
    if (window instanceof IdeFrame) {
      return ((IdeFrame)window).getStatusBar();
    }
    return null;
  }
}
