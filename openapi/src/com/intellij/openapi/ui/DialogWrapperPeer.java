/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyListener;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

public abstract class DialogWrapperPeer {
  public abstract void setUndecorated(boolean undecorated);

  /**
   * @see java.awt.Component#addMouseListener
   */
  public abstract void addMouseListener(MouseListener listener);

  /**
   * @see java.awt.Component#addMouseMotionListener
   */
  public abstract void addMouseListener(MouseMotionListener listener);

  /**
   * @see java.awt.Component#addKeyListener
   */
  public abstract void addKeyListener(KeyListener listener);

  /**
   * @see java.awt.Window#toFront()
   */
  public abstract void toFront();

  /**
   * @see java.awt.Window#toBack()
   */
  public abstract void toBack();

  /**
   * Dispose the wrapped and releases all resources allocated be the wrapper to help
   * more effecient garbage collection. You should never invoke this method twice or
   * invoke any method of the wrapper after invocation of <code>dispose</code>.
   */
  protected abstract void dispose();

  /**
   * @see javax.swing.JDialog#getContentPane
   */
  public abstract Container getContentPane();

  /**
   * @see java.awt.Window#getOwner
   */
  public abstract Window getOwner();

  public abstract Window getWindow();

  /**
   * @see javax.swing.JDialog#getRootPane
   */
  public abstract JRootPane getRootPane();

  /**
   * @see java.awt.Window#getSize
   */
  public abstract Dimension getSize();

  /**
   * @see java.awt.Dialog#getTitle
   */
  public abstract String getTitle();

  public abstract Dimension getPreferredSize();

  public abstract void setModal(boolean modal);

  /**
   * @see java.awt.Component#isVisible
   */
  public abstract boolean isVisible();

  /**
   * @see java.awt.Window#isShowing
   */
  public abstract boolean isShowing();

  /**
   * @see javax.swing.JDialog#setSize
   */
  public abstract void setSize(int width, int height);

  /**
   * @see javax.swing.JDialog#setTitle
   */
  public abstract void setTitle(String title);

  /**
   * @see javax.swing.JDialog#isResizable
   */
  public abstract void isResizable();

  /**
   * @see javax.swing.JDialog#setResizable
   */
  public abstract void setResizable(boolean resizable);

  /**
   * @see javax.swing.JDialog#getLocation
   */
  public abstract Point getLocation();

  /**
   * @see javax.swing.JDialog#setLocation(java.awt.Point)
   */
  public abstract void setLocation(Point p);

  /**
   * @see javax.swing.JDialog#setLocation(int,int)
   */
  public abstract void setLocation(int x, int y);

  public abstract void show();

  public abstract void setContentPane(JComponent content);

  public abstract void centerInParent();

  public abstract void validate();
  public abstract void repaint();
  public abstract void pack();
}