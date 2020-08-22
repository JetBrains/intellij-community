// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui;

import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyListener;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

public abstract class DialogWrapperPeer {
  public static final Object HAVE_INITIAL_SELECTION = ObjectUtils.sentinel("DialogWrapperPeer.HAVE_INITIAL_SELECTION");

  public abstract void setUndecorated(boolean undecorated);

  /**
   * @see Component#addMouseListener
   */
  public abstract void addMouseListener(MouseListener listener);

  /**
   * @see Component#addMouseMotionListener
   */
  public abstract void addMouseListener(MouseMotionListener listener);

  /**
   * @see Component#addKeyListener
   */
  public abstract void addKeyListener(KeyListener listener);

  /**
   * @see Window#toFront()
   */
  public abstract void toFront();

  /**
   * @see Window#toBack()
   */
  public abstract void toBack();

  /**
   * Dispose the wrapped and releases all resources allocated be the wrapper to help
   * more efficient garbage collection. You should never invoke this method twice or
   * invoke any method of the wrapper after invocation of {@code dispose}.
   */
  protected abstract void dispose();

  /**
   * @see JDialog#getContentPane
   */
  @Nullable
  public abstract Container getContentPane();

  /**
   * @see Window#getOwner
   */
  public abstract Window getOwner();

  public abstract Window getWindow();

  /**
   * @see JDialog#getRootPane
   */
  public abstract JRootPane getRootPane();

  /**
   * @see Window#getSize
   */
  public abstract Dimension getSize();

  /**
   * @see Dialog#getTitle
   */
  public abstract @NlsContexts.DialogTitle String getTitle();

  public abstract Dimension getPreferredSize();

  public abstract void setModal(boolean modal);

  public abstract boolean isModal();

  /**
   * @see Component#isVisible
   */
  public abstract boolean isVisible();

  /**
   * @see Window#isShowing
   */
  public abstract boolean isShowing();

  /**
   * @see JDialog#setSize
   */
  public abstract void setSize(int width, int height);

  /**
   * @see JDialog#setTitle
   */
  public abstract void setTitle(@NlsContexts.DialogTitle String title);

  /**
   * @see JDialog#isResizable
   */
  public abstract void isResizable();

  /**
   * @see JDialog#setResizable
   */
  public abstract void setResizable(boolean resizable);

  /**
   * @see JDialog#getLocation
   */
  @NotNull
  public abstract Point getLocation();

  /**
   * @see JDialog#setLocation(Point)
   */
  public abstract void setLocation(@NotNull Point p);

  /**
   * @see JDialog#setLocation(int,int)
   */
  public abstract void setLocation(int x, int y);

  public abstract ActionCallback show();

  public abstract void setContentPane(JComponent content);

  public abstract void centerInParent();

  public abstract void validate();
  public abstract void repaint();
  public abstract void pack();

  public abstract void setAppIcons();

  public abstract boolean isHeadless();

  public Object[] getCurrentModalEntities() {
    return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
  }
}