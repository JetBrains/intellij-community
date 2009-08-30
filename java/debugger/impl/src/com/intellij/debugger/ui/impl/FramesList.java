/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.ui.impl;

import com.intellij.debugger.ui.impl.watch.StackFrameDescriptorImpl;
import com.intellij.openapi.util.Comparing;
import com.intellij.xdebugger.impl.frame.DebuggerFramesList;
import com.sun.jdi.Method;

import javax.swing.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 7, 2006
 */
public class FramesList extends DebuggerFramesList {
  private volatile Method mySelectedMethod = null;

  protected FramesListRenderer createListRenderer() {
    return new FramesListRenderer();
  }

  protected void onFrameChanged(final Object selectedValue) {
    final StackFrameDescriptorImpl descriptor = selectedValue instanceof StackFrameDescriptorImpl? (StackFrameDescriptorImpl)selectedValue : null;
    final Method newMethod = descriptor != null? descriptor.getMethod() : null;
    if (!Comparing.equal(mySelectedMethod, newMethod)) {
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          repaint();
        }
      });
    }
    mySelectedMethod = newMethod;
  }

}
