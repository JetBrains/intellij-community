/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.ui;

import com.intellij.openapi.util.ActionCallback;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyListener;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

public abstract class DialogWrapperPeer {
  public static Object HAVE_INITIAL_SELECTION = new Object();

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
   * more efficient garbage collection. You should never invoke this method twice or
   * invoke any method of the wrapper after invocation of {@code dispose}.
   */
  protected abstract void dispose();

  /**
   * @see javax.swing.JDialog#getContentPane
   */
  @Nullable
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

  public abstract boolean isModal();

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
  @NotNull
  public abstract Point getLocation();

  /**
   * @see javax.swing.JDialog#setLocation(java.awt.Point)
   */
  public abstract void setLocation(@NotNull Point p);

  /**
   * @see javax.swing.JDialog#setLocation(int,int)
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
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }
}