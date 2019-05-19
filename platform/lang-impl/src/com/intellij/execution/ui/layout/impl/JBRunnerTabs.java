// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui.layout.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.tabs.*;
import com.intellij.ui.tabs.newImpl.SameHeightTabs;
import com.intellij.ui.tabs.newImpl.TabLabel;
import com.intellij.ui.tabs.newImpl.singleRow.ScrollableSingleRowLayout;
import com.intellij.ui.tabs.newImpl.singleRow.SingleRowLayout;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

/**
 * @author Dennis.Ushakov
 */
public class JBRunnerTabs extends SameHeightTabs implements JBRunnerTabsBase {
  public static JBRunnerTabsBase create(@Nullable Project project, @NotNull Disposable parentDisposable) {
    IdeFocusManager focusManager = project != null ? IdeFocusManager.getInstance(project) : null;
    return JBTabsFactory.getUseNewTabs()
           ? new JBRunnerTabs(project, ActionManager.getInstance(), focusManager, parentDisposable)
           : new JBRunnerTabsOld(project, ActionManager.getInstance(), focusManager, parentDisposable);
  }

  @Override
  protected JBTabPainter createTabPainter() {
    return JBTabPainter.getDEBUGGER();
  }

  public JBRunnerTabs(@Nullable Project project, @NotNull ActionManager actionManager, IdeFocusManager focusManager, @NotNull Disposable parent) {
    super(project, actionManager, focusManager, parent);
  }

  @Override
  protected SingleRowLayout createSingleRowLayout() {
    return new ScrollableSingleRowLayout(this);
  }

  @Override
  protected JBTabsBorder createTabBorder() {
    return new JBTabsBorder(this) {
      @NotNull
      @Override
      public Insets getEffectiveBorder() {
        return new Insets(getBorderThickness(), getBorderThickness(), 0, 0);
      }

      @Override
      public void paintBorder(@NotNull Component c, @NotNull Graphics g, int x, int y, int width, int height) {
        if (isEmptyVisible()) return;
        getTabPainter().paintBorderLine((Graphics2D)g, getBorderThickness(), new Point(x, y), new Point(x, y + height));
        getTabPainter()
          .paintBorderLine((Graphics2D)g, getBorderThickness(), new Point(x, y + myHeaderFitSize.height),
                           new Point(x + width, y + myHeaderFitSize.height));
      }
    };
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
  protected TabLabel createTabLabel(TabInfo info) {
    return new SingleHeightLabel(this, info) {
      @Override
      public void setTabActionsAutoHide(boolean autoHide) {
        super.setTabActionsAutoHide(autoHide);
        apply(new UiDecorator.UiDecoration(null, JBUI.insets(0, 8, 0, 8)));
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
    };

  }

}
