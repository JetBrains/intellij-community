// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.AnActionLink;
import com.intellij.ui.components.JBBox;
import com.intellij.util.ui.JBUI;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointCustomPropertiesPanel;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.debugger.breakpoints.properties.JavaCollectionBreakpointProperties;

import javax.swing.*;
import java.awt.*;

@ApiStatus.Experimental
public class CollectionBreakpointPropertiesPanel
  extends XBreakpointCustomPropertiesPanel<XLineBreakpoint<JavaCollectionBreakpointProperties>> {
  private static final int PREFERRED_PANEL_HEIGHT = 40;
  private @Nullable String myClsName = null;
  private @Nullable String myFieldName = null;
  private JCheckBox mySaveCollectionHistoryCheckBox;

  @Override
  public @NotNull JComponent getComponent() {
    mySaveCollectionHistoryCheckBox =
      new JCheckBox(JavaDebuggerBundle.message("label.collection.breakpoint.properties.save.history"));
    AnActionLink button =
      new AnActionLink(ActionsBundle.message("action.Debugger.ShowCollectionHistory.text"), new MyShowCollectionHistoryAction());

    JBBox box = JBBox.createVerticalBox();

    JPanel panel = JBUI.Panels.simplePanel();
    panel.add(mySaveCollectionHistoryCheckBox, BorderLayout.NORTH);
    mySaveCollectionHistoryCheckBox.setPreferredSize(new Dimension(panel.getPreferredSize().width, PREFERRED_PANEL_HEIGHT));
    box.add(panel);

    panel = JBUI.Panels.simplePanel();
    panel.add(button);
    button.setPreferredSize(new Dimension(panel.getPreferredSize().width, PREFERRED_PANEL_HEIGHT));
    box.add(panel);

    panel = JBUI.Panels.simplePanel();
    panel.add(box);

    return panel;
  }

  @Override
  public void saveTo(@NotNull XLineBreakpoint<JavaCollectionBreakpointProperties> breakpoint) {
    boolean changed = breakpoint.getProperties().SHOULD_SAVE_COLLECTION_HISTORY != mySaveCollectionHistoryCheckBox.isSelected();
    breakpoint.getProperties().SHOULD_SAVE_COLLECTION_HISTORY = mySaveCollectionHistoryCheckBox.isSelected();
    if (changed) {
      ((XBreakpointBase<?, ?, ?>)breakpoint).fireBreakpointChanged();
    }
  }

  @Override
  public void loadFrom(@NotNull XLineBreakpoint<JavaCollectionBreakpointProperties> breakpoint) {
    JavaCollectionBreakpointProperties properties = breakpoint.getProperties();
    myClsName = properties.myClassName;
    myFieldName = properties.myFieldName;
    mySaveCollectionHistoryCheckBox.setSelected(properties.SHOULD_SAVE_COLLECTION_HISTORY);
  }

  private class MyShowCollectionHistoryAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      String clsName = myClsName;
      String fieldName = myFieldName;
      if (clsName == null || fieldName == null) {
        return;
      }
      Project project = getEventProject(e);
      if (project == null) {
        return;
      }
      XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
      if (session == null) {
        return;
      }
      DebuggerUtilsEx.addCollectionHistoryTab(session, clsName, fieldName, null);
    }
  }
}