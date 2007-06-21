/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.ui.impl;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.ui.impl.watch.StackFrameDescriptorImpl;
import com.intellij.ide.OccurenceNavigator;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.ListToolTipHandler;
import com.sun.jdi.Method;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 7, 2006
 */
public class FramesList extends JList implements OccurenceNavigator {
  private volatile Method mySelectedMethod = null;

  public FramesList() {
    super(new DefaultListModel());
    getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    setCellRenderer(new FramesListRenderer());
    ListToolTipHandler.install(this);
    getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(final ListSelectionEvent e) {
        if (!e.getValueIsAdjusting()) {
          final Object selectedValue = getSelectedValue();
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
    });
  }

  public DefaultListModel getModel() {
    return (DefaultListModel)super.getModel();
  }

  public void clear() {
    getModel().clear();
  }

  public int getElementCount() {
    return getModel().getSize();
  }
  // OccurenceNavigator implementation
  public String getNextOccurenceActionName() {
    return DebuggerBundle.message("action.next.frame.text");
  }

  public String getPreviousOccurenceActionName() {
    return DebuggerBundle.message("action.previous.frame.text");
  }

  public OccurenceInfo goFirstOccurence() {
    setSelectedIndex(0);
    return createInfo();
  }

  public OccurenceInfo goNextOccurence() {
    setSelectedIndex(getSelectedIndex() + 1);
    return createInfo();
  }

  public OccurenceInfo goPreviousOccurence() {
    setSelectedIndex(getSelectedIndex() - 1);
    return createInfo();
  }

  private OccurenceInfo createInfo() {
    return OccurenceInfo.position(getSelectedIndex(), getElementCount());
  }

  public boolean hasNextOccurence() {
    return getSelectedIndex() < getElementCount() - 1;
  }

  public boolean hasPreviousOccurence() {
    return getSelectedIndex() > 0;
  }
}
