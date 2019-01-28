/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.ui.tabs.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.registry.RegistryValueListener;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.tabs.JBTabsPosition;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.impl.singleRow.CompressibleSingleRowLayout;
import com.intellij.ui.tabs.impl.singleRow.ScrollableSingleRowLayout;
import com.intellij.ui.tabs.impl.singleRow.SingleRowLayout;
import com.intellij.ui.tabs.impl.tabPainters.JBDefaultTabPainter;
import com.intellij.ui.tabs.impl.tabPainters.JBTabPainter;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

/**
 * @author pegov
 */
public class JBEditorTabs extends JBTabsImpl {
  public static final String TABS_ALPHABETICAL_KEY = "tabs.alphabetical";
  protected JBEditorTabsPainter myDefaultPainter = createEditorTabsPainter();
  protected JBTabPainter tabPainter = createTabPainter();

  public JBEditorTabs(@Nullable Project project, @NotNull ActionManager actionManager, IdeFocusManager focusManager, @NotNull Disposable parent) {
    super(project, actionManager, focusManager, parent);
    Registry.get(TABS_ALPHABETICAL_KEY).addListener(new RegistryValueListener.Adapter() {

      @Override
      public void afterValueChanged(@NotNull RegistryValue value) {
        ApplicationManager.getApplication().invokeLater(() -> {
          resetTabsCache();
          relayout(true, false);
        });
      }
    }, parent);
  }

  protected JBTabPainter createTabPainter() {
    return new JBDefaultTabPainter();
  }

  protected JBEditorTabsPainter createEditorTabsPainter() {
    return new DefaultEditorTabsPainter(this);
  }

  @Override
  protected SingleRowLayout createSingleRowLayout() {
    if (!UISettings.getInstance().getHideTabsIfNeed() && supportsCompression()) {
      return new CompressibleSingleRowLayout(this);
    }
    else if (ApplicationManager.getApplication().isInternal() || Registry.is("editor.use.scrollable.tabs")) {
      return new ScrollableSingleRowLayout(this);
    }
    return super.createSingleRowLayout();
  }

  @Override
  public boolean supportsCompression() {
    return true;
  }

  @Nullable
  public Rectangle getSelectedBounds() {
    TabLabel label = getSelectedLabel();
    return label != null ? label.getBounds() : null;
  }

  @Override
  public boolean isEditorTabs() {
    return true;
  }

  @Override
  protected void paintFirstGhost(Graphics2D g2d) {
  }

  @Override
  protected void paintLastGhost(Graphics2D g2d) {
  }

  @Override
  public boolean isGhostsAlwaysVisible() {
    return false;
  }

  @Override
  public boolean useSmallLabels() {
    return UISettings.getInstance().getUseSmallLabelsOnTabs();
  }

  @Override
  public boolean useBoldLabels() {
    return SystemInfo.isMac && Registry.is("ide.mac.boldEditorTabs");
  }

  @Override
  protected void doPaintInactive(Graphics2D g2d,
                                 boolean leftGhostExists,
                                 TabLabel label,
                                 Rectangle effectiveBounds,
                                 boolean rightGhostExists, int row, int column) {

    final Color tabColor = label.getInfo().getTabColor();
    tabPainter.paintTab(g2d, label.getBounds(), tabColor);
  }

  @Override
  protected void doPaintSelected(Graphics2D g2d,
                                 boolean leftGhostExists,
                                 TabLabel label,
                                 Rectangle effectiveBounds,
                                 boolean rightGhostExists, int row, int column) {

    final Color tabColor = label.getInfo().getTabColor();
    tabPainter.paintSelectedTab(g2d, label.getBounds(), tabColor, getPosition(), true);
  }

  protected JBEditorTabsPainter getPainter() {
    return myDefaultPainter;
  }

  @Override
  public boolean isAlphabeticalMode() {
    return Registry.is(TABS_ALPHABETICAL_KEY);
  }

  public static void setAlphabeticalMode(boolean on) {
    Registry.get(TABS_ALPHABETICAL_KEY).setValue(on);
  }

  @Override
  protected void paintSelectionAndBorder(Graphics2D g2d) {
    if (getSelectedInfo() == null || isHideTabs()) return;

    TabLabel label = getSelectedLabel();
    Color tabColor = label.getInfo().getTabColor();

    tabPainter.paintSelectedTab(g2d, label.getBounds(), tabColor, getPosition(), true);
  }

  @Override
  public Color getBackground() {
    return getPainter().getBackgroundColor();
  }

  @Override
  public Color getForeground() {
    return UIUtil.getLabelForeground();
  }

  protected ShapeInfo _computeSelectedLabelShape() {
    final ShapeInfo shape = new ShapeInfo();

    shape.path = getEffectiveLayout().createShapeTransform(getSize());
    shape.insets = shape.path.transformInsets(getLayoutInsets());
    shape.labelPath = shape.path.createTransform(getSelectedLabel().getBounds());

    shape.labelBottomY = shape.labelPath.getMaxY();
    boolean isTop = getPosition() == JBTabsPosition.top;
    boolean isBottom = getPosition() == JBTabsPosition.bottom;
    shape.labelTopY =
      shape.labelPath.getY() + (isTop ? shape.labelPath.deltaY(1) : isBottom ? shape.labelPath.deltaY(-1) : 0) ;
    shape.labelLeftX = shape.labelPath.getX() + (isTop || isBottom ? 0 : shape.labelPath.deltaX(1));
    shape.labelRightX = shape.labelPath.getMaxX() /*- shape.labelPath.deltaX(1)*/;

    int leftX = shape.insets.left + (isTop || isBottom ? 0 : shape.labelPath.deltaX(1));

    shape.path.moveTo(leftX, shape.labelBottomY);
    shape.path.lineTo(shape.labelLeftX, shape.labelBottomY);
    shape.path.lineTo(shape.labelLeftX, shape.labelTopY);
    shape.path.lineTo(shape.labelRightX, shape.labelTopY);
    shape.path.lineTo(shape.labelRightX, shape.labelBottomY);

    int lastX = shape.path.getWidth() - shape.path.deltaX(shape.insets.right);

    shape.path.lineTo(lastX, shape.labelBottomY);
    shape.path.lineTo(leftX, shape.labelBottomY);

    shape.path.closePath();
    shape.fillPath = shape.path.copy();

    return shape;
  }
}
