// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui.layout.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.SideBorder;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.tabs.JBTabPainter;
import com.intellij.ui.tabs.JBTabsBorder;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.impl.*;
import com.intellij.ui.tabs.impl.singleRow.ScrollableSingleRowLayout;
import com.intellij.ui.tabs.impl.singleRow.SingleRowLayout;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

/**
 * @author Dennis.Ushakov
 */
public class JBRunnerTabs extends SingleHeightTabs implements JBRunnerTabsBase {
  public static JBRunnerTabsBase create(@Nullable Project project, @NotNull Disposable parentDisposable) {
    return new JBRunnerTabs(project, parentDisposable);
  }

  @Override
  protected TabPainterAdapter createTabPainterAdapter() {
    return new DefaultTabPainterAdapter(JBTabPainter.getDEBUGGER());
  }

  public JBRunnerTabs(@Nullable Project project, @NotNull Disposable parent) {
    super(project, parent);
  }

  /**
   * @deprecated Use {@link #JBRunnerTabs(Project, Disposable)}
   */
  @SuppressWarnings("unused")
  @Deprecated
  public JBRunnerTabs(@Nullable Project project, @NotNull ActionManager actionManager, IdeFocusManager focusManager, @NotNull Disposable parent) {
    super(project, parent);
  }

  @Override
  protected SingleRowLayout createSingleRowLayout() {
    return new ScrollableSingleRowLayout(this);
  }

  @Override
  protected JBTabsBorder createTabBorder() {
    return new JBRunnerTabsBorder(this);
  }

  @Override
  public boolean useSmallLabels() {
    return !ExperimentalUI.isNewUI();
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

  /**
   * @return scaled preferred runner tab label height aligned with toolbars
   */
  public static int getTabLabelPreferredHeight() {
    return ExperimentalUI.isNewUI() ? JBUI.scale(JBUI.CurrentTheme.DebuggerTabs.tabHeight())
                                    : JBUI.scale(29);
  }

  @NotNull
  @Override
  protected TabLabel createTabLabel(@NotNull TabInfo info) {
    return new SingleHeightLabel(this, info) {
      {
        updateFont();
      }

      @Override
      public void updateUI() {
        super.updateUI();
        updateFont();
      }

      private void updateFont() {
        JComponent label = getLabelComponent();
        if (label != null && ExperimentalUI.isNewUI()) {  // can be null at the first updateUI call during init
          label.setFont(JBUI.CurrentTheme.DebuggerTabs.font());
        }
      }

      @Override
      protected int getActionsInset() {
        return 8;
      }

      @Override
      protected int getPreferredHeight() {
        return getTabLabelPreferredHeight();
      }
    };

  }

  public class JBRunnerTabsBorder extends JBTabsBorder {
    private int mySideMask = SideBorder.LEFT;

    JBRunnerTabsBorder(@NotNull JBTabsImpl tabs) {
      super(tabs);
    }

    @NotNull
    @Override
    public Insets getEffectiveBorder() {
      //noinspection UseDPIAwareInsets
      return new Insets(getBorderThickness(), (mySideMask & SideBorder.LEFT) != 0 ? getBorderThickness() : 0, 0, 0);
    }

    @Override
    public void paintBorder(@NotNull Component c, @NotNull Graphics g, int x, int y, int width, int height) {
      if (isEmptyVisible()) return;

      if ((mySideMask & SideBorder.LEFT) != 0) {
        getTabPainter().paintBorderLine((Graphics2D)g, getBorderThickness(), new Point(x, y), new Point(x, y + height));
      }

      getTabPainter()
        .paintBorderLine((Graphics2D)g, getBorderThickness(), new Point(x, y + myHeaderFitSize.height),
                         new Point(x + width, y + myHeaderFitSize.height));
    }

    public void setSideMask(@SideBorder.SideMask int mask) {
      mySideMask = mask;
    }
  }
}
