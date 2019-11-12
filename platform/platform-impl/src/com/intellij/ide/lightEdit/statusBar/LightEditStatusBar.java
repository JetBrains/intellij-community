// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit.statusBar;

import com.intellij.ide.lightEdit.LightEditUtil;
import com.intellij.ide.lightEdit.LightEditorManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.impl.status.IdeStatusBarImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class LightEditStatusBar extends LightEditStatusBarBase implements Disposable {
  private final LightEditorManager     myEditorManager;
  private final JPanel                 myRightPanel;
  private final Map<String,WidgetData> myWidgets = new HashMap<>();

  public LightEditStatusBar(LightEditorManager editorManager) {
    myEditorManager = editorManager;
    setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
    setBorder(BorderFactory.createEmptyBorder(1, 0, 0, 0));
    add(Box.createHorizontalGlue());
    myRightPanel = createRightPanel();
    add(myRightPanel);
    createWidgets();
  }

  private static JPanel createRightPanel() {
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.LINE_AXIS));
    JLabel label = new JLabel("|");
    panel.add(Box.createRigidArea(label.getPreferredSize()));
    return panel;
  }

  private void createWidgets() {
    addWidgetImpl(new LightEditPositionWidget());
  }

  private void addWidgetImpl(@NotNull StatusBarWidget widget) {
    widget.install(this);
    JComponent wrapper = IdeStatusBarImpl.wrap(widget);
    myRightPanel.add(wrapper);
    myWidgets.put(widget.ID(), new WidgetData(widget, wrapper));
  }

  @Override
  public void dispose() {
    myWidgets.values().stream()
      .map(data -> data.widget).forEach(widget -> Disposer.dispose(widget));
  }

  @Override
  public void updateWidget(@NotNull String id) {
    WidgetData data = myWidgets.get(id);
    if (data != null) {
      if (data.component instanceof IdeStatusBarImpl.StatusBarWrapper) {
        ((IdeStatusBarImpl.StatusBarWrapper)data.component).beforeUpdate();
      }
      data.component.repaint();
    }
  }

  @Nullable
  @Override
  public StatusBarWidget getWidget(String id) {
    final WidgetData data = myWidgets.get(id);
    return data != null ? data.widget : null;
  }

  @Override
  public void fireNotificationPopup(@NotNull JComponent content, Color backgroundColor) {
  }

  @Override
  public JComponent getComponent() {
    return this;
  }

  @Nullable
  @Override
  public Project getProject() {
    return LightEditUtil.getProject();
  }

  @Override
  public void setInfo(@Nullable String s) {

  }

  @Override
  public void setInfo(@Nullable String s, @Nullable String requestor) {

  }

  @Override
  public String getInfo() {
    return null;
  }

  public LightEditorManager getEditorManager() {
    return myEditorManager;
  }

  private static class WidgetData {
    private final StatusBarWidget widget;
    private final JComponent      component;

    private WidgetData(StatusBarWidget widget, JComponent component) {
      this.widget = widget;
      this.component = component;
    }
  }
}
