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
package com.intellij.execution.ui.layout.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.UiDecorator;
import com.intellij.ui.tabs.impl.JBEditorTabs;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.ui.tabs.impl.TabLabel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Map;

/**
 * @author Dennis.Ushakov
 */
public class JBRunnerTabs extends JBEditorTabs {
  public JBRunnerTabs(@Nullable Project project, @NotNull ActionManager actionManager, IdeFocusManager focusManager, @NotNull Disposable parent) {
    super(project, actionManager, focusManager, parent);
  }

  @Override
  public boolean useSmallLabels() {
    return true;
  }

  @Override
  public int getToolbarInset() {
    return 0;
  }

  @Override
  public int tabMSize() {
    return 8;
  }

  public boolean shouldAddToGlobal(Point point) {
    final TabLabel label = getSelectedLabel();
    if (label == null || point == null) {
      return true;
    }
    final Rectangle bounds = label.getBounds();
    return point.y <= bounds.y + bounds.height;
  }

  @Override
  public Rectangle layout(JComponent c, Rectangle bounds) {
    if (c instanceof Toolbar) {
      bounds.height -= 5;
      return super.layout(c, bounds);
    }
    return super.layout(c, bounds);
  }

  @Override
  public void processDropOver(TabInfo over, RelativePoint relativePoint) {
    final Point point = relativePoint.getPoint(getComponent());
    myShowDropLocation = shouldAddToGlobal(point);
    super.processDropOver(over, relativePoint);
    for (Map.Entry<TabInfo, TabLabel> entry : myInfo2Label.entrySet()) {
      final TabLabel label = entry.getValue();
      if (label.getBounds().contains(point) && myDropInfo != entry.getKey()) {
        select(entry.getKey(), false);
        break;
      }
    }
  }

  @Override
  protected Color getEmptySpaceColor() {
    return UIUtil.getBgFillColor(getParent());
  }

  @Override
  protected TabLabel createTabLabel(TabInfo info) {
    return new MyTabLabel(this, info);
  }

  private static class MyTabLabel extends TabLabel {
    public MyTabLabel(JBTabsImpl tabs, final TabInfo info) {
      super(tabs, info);
    }

    @Override
    public void apply(UiDecorator.UiDecoration decoration) {
      setBorder(new EmptyBorder(5, 5, 7, 5));
    }

    @Override
    public void setTabActionsAutoHide(boolean autoHide) {
      super.setTabActionsAutoHide(autoHide);
      apply(null);
    }

    @Override
    public void setTabActions(ActionGroup group) {
      super.setTabActions(group);
      if (myActionPanel != null) {
        final JComponent wrapper = (JComponent)myActionPanel.getComponent(0);
        wrapper.remove(0);
        wrapper.add(Box.createHorizontalStrut(6), BorderLayout.WEST);
      }
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension result = super.getPreferredSize();
      result.height += myTabs.getActiveTabUnderlineHeight() - 1;
      return result;
    }
  }
}
