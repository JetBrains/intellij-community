// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.ui.IdeUICustomization;
import com.intellij.ui.RelativeFont;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

class Banner extends NonOpaquePanel implements PropertyChangeListener{
  private int myBannerMinHeight;
  private final JComponent myText = new MyText();
  private final JLabel myProjectIcon = new JLabel(AllIcons.General.ProjectConfigurable, SwingConstants.LEFT);
  private final NonOpaquePanel myActionsPanel = new NonOpaquePanel(new FlowLayout(FlowLayout.RIGHT, 2, 2));

  private final Map<Action, ActionLink> myActions = new HashMap<>();

  Banner() {
    setLayout(new BorderLayout());

    setBorder(JBUI.Borders.empty(2, 12, 2, 4));

    myProjectIcon.setVisible(false);
    myProjectIcon.setBorder(new EmptyBorder(0, 12, 0, 4));
    add(myText, BorderLayout.WEST);
    add(myProjectIcon, BorderLayout.CENTER);
    add(myActionsPanel, BorderLayout.EAST);
  }

  public void addAction(final Action action) {
    action.addPropertyChangeListener(this);
    ActionLink label = new ActionLink("", action);
    label.setFont(label.getFont().deriveFont(Font.BOLD));
    myActions.put(action, label);
    myActionsPanel.add(label);
    updateAction(action);
  }

  void updateAction(Action action) {
    ActionLink label = myActions.get(action);
    label.setVisible(action.isEnabled());
    label.setText((String)action.getValue(Action.NAME));
    label.setToolTipText((String)action.getValue(Action.SHORT_DESCRIPTION));
  }

  @Override
  public void propertyChange(final PropertyChangeEvent evt) {
    final Object source = evt.getSource();
    if (source instanceof Action) {
      updateAction((Action)source);
    }
  }

  public void clearActions() {
    final Set<Action> actions = myActions.keySet();
    for (Action each : actions) {
      each.removePropertyChangeListener(this);
    }
    myActions.clear();
    myActionsPanel.removeAll();
  }

  @Override
  public Dimension getMinimumSize() {
    final Dimension size = super.getMinimumSize();
    size.height = Math.max(myBannerMinHeight, size.height);
    return size;
  }

  @Override
  public Dimension getPreferredSize() {
    final Dimension size = super.getPreferredSize();
    size.height = getMinimumSize().height;
    return size;
  }

  public void setMinHeight(final int height) {
    myBannerMinHeight = height;
    revalidate();
    repaint();
  }

  public void forProject(Project project) {
    if (project != null) {
      myProjectIcon.setVisible(true);
      myProjectIcon.setText(project.isDefault()
                            ? IdeUICustomization.getInstance().projectMessage("configurable.default.project.tooltip")
                            : IdeUICustomization.getInstance().projectMessage("configurable.current.project.tooltip"));
    } else {
      myProjectIcon.setVisible(false);
    }
  }

  public void setText(final String @NotNull ... text) {
    myText.removeAll();
    for (int i = 0; i < text.length; i++) {
      final JLabel eachLabel = new JLabel(text[i], SwingConstants.CENTER);
      eachLabel.setBorder(new EmptyBorder(0, 0, 0, 5));
      eachLabel.setFont(RelativeFont.BOLD.derive(eachLabel.getFont()));
      myText.add(eachLabel);
      if (i < text.length - 1) {
        final JLabel eachIcon = new JLabel("\u203A", SwingConstants.CENTER);
        eachIcon.setFont(RelativeFont.HUGE.derive(eachIcon.getFont()));
        eachIcon.setBorder(new EmptyBorder(0, 0, 0, 5));
        myText.add(eachIcon);
      }
    }
  }

  public void updateActions() {
    final Set<Action> actions = myActions.keySet();
    for (Action action : actions) {
      updateAction(action);
    }
  }


  private static class MyText extends NonOpaquePanel {
    @Override
    public void doLayout() {
      int x = 0;
      for (int i = 0; i < getComponentCount(); i++) {
        final Component each = getComponent(i);
        final Dimension eachSize = each.getPreferredSize();
        each.setBounds(x, 0, eachSize.width, getHeight());
        x += each.getBounds().width;
      }
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension size = new Dimension();
      for (int i = 0; i < getComponentCount(); i++) {
        final Component each = getComponent(i);
        final Dimension eachSize = each.getPreferredSize();
        size.width += eachSize.width;
        size.height = Math.max(size.height, eachSize.height);
      }

      return size;
    }
  }
}
