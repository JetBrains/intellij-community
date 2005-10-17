/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.ui;

import com.intellij.util.WeakListener;

import javax.swing.*;
import java.awt.event.MouseListener;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 25, 2004
 */
public class WeakMouseListener extends WeakListener<JComponent, MouseListener> {
  public WeakMouseListener(JComponent source, MouseListener listenerImpl) {
    super(source, MouseListener.class, listenerImpl);
  }
  public void addListener(JComponent source, MouseListener listener) {
    source.addMouseListener(listener);
  }
  public void removeListener(JComponent source, MouseListener listener) {
    source.removeMouseListener(listener);
  }
}
