/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.ui.impl;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.ide.OccurenceNavigator;
import com.intellij.ui.ListToolTipHandler;

import javax.swing.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 7, 2006
 */
public class FramesList extends JList implements OccurenceNavigator {

  public FramesList() {
    super(new DefaultListModel());
    getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    setCellRenderer(new FramesListRenderer());
    ListToolTipHandler.install(this);
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
