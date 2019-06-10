// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.tabs.JBEditorTabsBase;
import com.intellij.ui.tabs.JBTabsPosition;
import com.intellij.ui.tabs.JBTabsPresentation;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.impl.singleRow.CompressibleSingleRowLayout;
import com.intellij.ui.tabs.impl.singleRow.ScrollableSingleRowLayout;
import com.intellij.ui.tabs.impl.singleRow.SingleRowLayout;
import com.intellij.ui.tabs.impl.table.TableLayout;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;
import java.util.function.Supplier;

/**
 * @author pegov
 */
public class JBEditorTabs extends JBTabsImpl implements JBEditorTabsBase {
  protected JBEditorTabsPainter myDefaultPainter = new DefaultEditorTabsPainter(this);
  private boolean myAlphabeticalModeChanged = false;
  @Nullable
  private Supplier<? extends Color> myEmptySpaceColorCallback;

  public JBEditorTabs(@Nullable Project project, @NotNull ActionManager actionManager, IdeFocusManager focusManager, @NotNull Disposable parent) {
    super(project, actionManager, focusManager, parent);
    ApplicationManager.getApplication().getMessageBus().connect(parent).subscribe(UISettingsListener.TOPIC, (settings) -> {
      ApplicationManager.getApplication().invokeLater(() -> {
        resetTabsCache();
        relayout(true, false);
      });
    });
    setSupportsCompression(true);
  }

  @Override
  protected SingleRowLayout createSingleRowLayout() {
    if (!UISettings.getInstance().getHideTabsIfNeeded() && supportsCompression()) {
      return new CompressibleSingleRowLayout(this);
    }
    else if (ApplicationManager.getApplication().isInternal() || Registry.is("editor.use.scrollable.tabs")) {
      return new ScrollableSingleRowLayout(this);
    }
    return super.createSingleRowLayout();
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
  public boolean hasUnderline() {
    return isSingleRow() && !hasUnderlineSelection();
  }

  @Override
  protected void doPaintInactive(Graphics2D g2d,
                                 boolean leftGhostExists,
                                 TabLabel label,
                                 Rectangle effectiveBounds,
                                 boolean rightGhostExists, int row, int column) {
    Insets insets = getTabsBorder().getEffectiveBorder();

    int _x = effectiveBounds.x + insets.left;
    int _y = effectiveBounds.y + insets.top;
    int _width = effectiveBounds.width - insets.left - insets.right + (getTabsPosition() == JBTabsPosition.right ? 1 : 0);
    int _height = effectiveBounds.height - insets.top - insets.bottom;


    if ((!isSingleRow() /* for multiline */) || (isSingleRow() && isHorizontalTabs()))  {
      if (isSingleRow() && getPosition() == JBTabsPosition.bottom) {
        _y += getActiveTabUnderlineHeight();
      } else {
        if (isSingleRow()) {
          _height -= getActiveTabUnderlineHeight();
        } else {
          TabInfo info = label.getInfo();
          if (((TableLayout)getEffectiveLayout()).isLastRow(info)) {
            _height -= getActiveTabUnderlineHeight();
          }
        }
      }
    }

    final boolean vertical = getTabsPosition() == JBTabsPosition.left || getTabsPosition() == JBTabsPosition.right;
    final Color tabColor = label.getInfo().getTabColor();
    final Composite oldComposite = g2d.getComposite();
    //if (label != getSelectedLabel()) {
    //  g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.9f));
    //}
    getPainter().doPaintInactive(g2d, effectiveBounds, _x, _y, _width, _height, tabColor, row, column, vertical);
    //g2d.setComposite(oldComposite);
  }



  @Override
  public int getActiveTabUnderlineHeight() {
    return hasUnderline() ? super.getActiveTabUnderlineHeight() : hasUnderlineSelection() ? 0 : 1;
  }

  public boolean hasUnderlineSelection() {
    return false;
  }

  protected JBEditorTabsPainter getPainter() {
    return myDefaultPainter;
  }

  @Override
  public boolean isAlphabeticalMode() {
    if (myAlphabeticalModeChanged) {
      return super.isAlphabeticalMode();
    }
    return UISettings.getInstance().getSortTabsAlphabetically();
  }

  @Override
  public JBTabsPresentation setAlphabeticalMode(boolean alphabeticalMode) {
    myAlphabeticalModeChanged = true;
    return super.setAlphabeticalMode(alphabeticalMode);
  }

  @Override
  public void setEmptySpaceColorCallback(@NotNull Supplier<? extends Color> callback) {
    myEmptySpaceColorCallback = callback;
  }

  @Override
  protected void doPaintBackground(Graphics2D g2d, Rectangle clip) {
    List<TabInfo> visibleInfos = getVisibleInfos();
    final boolean vertical = getTabsPosition() == JBTabsPosition.left || getTabsPosition() == JBTabsPosition.right;

    Insets insets = getTabsBorder().getEffectiveBorder();

    int minOffset = vertical ? getHeight(): getWidth();
    int maxOffset = 0;
    int maxLength = 0;

    for (int i = visibleInfos.size() - 1; i >= 0; i--) {
      TabInfo visibleInfo = visibleInfos.get(i);
      TabLabel tabLabel = myInfo2Label.get(visibleInfo);
      Rectangle r = tabLabel.getBounds();
      if (r.width == 0 || r.height == 0) continue;
      minOffset = Math.min(vertical ? r.y : r.x, minOffset);
      maxOffset = Math.max(vertical ? r.y + r.height : r.x + r.width, maxOffset);
      maxLength = vertical ? r.width : r.height;
    }

    minOffset--;
    maxOffset++;

    Rectangle r2 = new Rectangle(0, 0, getWidth(), getHeight());

    Rectangle beforeTabs;
    Rectangle afterTabs;
    if (vertical) {
      int x = r2.x + insets.left;
      int width = maxLength - insets.left - insets.right;

      if (getTabsPosition() == JBTabsPosition.right) {
        x = r2.width - width - insets.left + getActiveTabUnderlineHeight();
      } else {
        width -= getActiveTabUnderlineHeight();
      }

      beforeTabs = new Rectangle(x, insets.top, width, minOffset - insets.top);
      afterTabs = new Rectangle(x, maxOffset, width, r2.height - maxOffset - insets.top - insets.bottom);
    } else {
      int y = r2.y + insets.top;
      int height = maxLength - insets.top - insets.bottom;
      if (getTabsPosition() == JBTabsPosition.bottom) {
        y = r2.height - height - insets.top + getActiveTabUnderlineHeight();
      } else {
        height -= getActiveTabUnderlineHeight();
      }

      beforeTabs = new Rectangle(insets.left, y, minOffset, height);
      afterTabs = new Rectangle(maxOffset, y, r2.width - maxOffset - insets.left - insets.right, height);
    }

    getPainter().doPaintBackground(g2d, clip, vertical, afterTabs);
    g2d.setPaint(getEmptySpaceColor());
    g2d.fill(beforeTabs);
    g2d.fill(afterTabs);
  }

  protected Color getEmptySpaceColor() {
    if (myEmptySpaceColorCallback != null) {
      return myEmptySpaceColorCallback.get();
    }
    return getPainter().getEmptySpaceColor();
  }

  @Override
  protected void paintSelectionAndBorder(Graphics2D g2d) {
    if (getSelectedInfo() == null || isHideTabs()) return;

    TabLabel label = getSelectedLabel();
    Rectangle r = label.getBounds();

    ShapeInfo selectedShape = _computeSelectedLabelShape();

    Insets insets = getTabsBorder().getEffectiveBorder();

    Color tabColor = label.getInfo().getTabColor();

    getPainter().paintSelectionAndBorder(g2d, r, selectedShape, insets, tabColor);
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

    shape.labelBottomY = shape.labelPath.getMaxY() - shape.labelPath.deltaY(getActiveTabUnderlineHeight() - 1);
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
    shape.path.lineTo(lastX, shape.labelBottomY + shape.labelPath.deltaY(Math.max(0, getActiveTabUnderlineHeight() - 1)));
    shape.path.lineTo(leftX, shape.labelBottomY + shape.labelPath.deltaY(Math.max(0, getActiveTabUnderlineHeight() - 1)));

    shape.path.closePath();
    shape.fillPath = shape.path.copy();

    return shape;
  }
}
