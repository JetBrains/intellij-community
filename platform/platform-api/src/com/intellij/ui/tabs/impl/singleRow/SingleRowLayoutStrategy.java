// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tabs.impl.singleRow;

import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.tabs.TabsUtil;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.ui.tabs.impl.ShapeTransform;
import com.intellij.ui.tabs.impl.TabLabel;
import com.intellij.ui.tabs.impl.TabLayout;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

@ApiStatus.Internal
public abstract class SingleRowLayoutStrategy {
  private static final int MIN_TAB_WIDTH = 50;
  final SingleRowLayout myLayout;
  final JBTabsImpl myTabs;

  protected SingleRowLayoutStrategy(SingleRowLayout layout) {
    myLayout = layout;
    myTabs = myLayout.tabs;
  }

  abstract int getMoreRectAxisSize();

  abstract int getEntryPointAxisSize();

  public abstract int getStartPosition(final SingleRowPassInfo data);

  public abstract int getToFitLength(final SingleRowPassInfo data);

  public abstract int getLengthIncrement(final Dimension dimension);

  public abstract int getAdditionalLength(SingleRowPassInfo data);

  public abstract int getMinPosition(final Rectangle bounds);

  public abstract int getMaxPosition(final Rectangle bounds);

  protected abstract int getFixedFitLength(final SingleRowPassInfo data);

  public Rectangle getLayoutRect(final SingleRowPassInfo data, final int position, final int length) {
    return getLayoutRec(data, position, getFixedPosition(data), length, getFixedFitLength(data));
  }

  protected abstract Rectangle getLayoutRec(final SingleRowPassInfo data,
                                            final int position,
                                            final int fixedPos,
                                            final int length,
                                            final int fixedFitLength);

  protected abstract int getFixedPosition(final SingleRowPassInfo data);

  protected abstract Rectangle getTitleRect(SingleRowPassInfo data);

  public abstract Rectangle getMoreRect(final SingleRowPassInfo data);

  public abstract Rectangle getEntryPointRect(final SingleRowPassInfo data);

  public abstract boolean isToCenterTextWhenStretched();

  public abstract ShapeTransform createShapeTransform(Rectangle rectangle);

  public abstract void layoutComp(SingleRowPassInfo data);

  public boolean isSideComponentOnTabs() {
    return false;
  }

  public abstract boolean isDragOut(TabLabel tabLabel, int deltaX, int deltaY);

  public @NotNull Point adjustDropPoint(@NotNull Point point) {
    return point;
  }

  /**
   * Whether a tab that didn't fit completely on the right/bottom side in scrollable layout should be clipped or hidden altogether.
   *
   * @return true if the tab should be clipped, false if hidden.
   */
  public abstract boolean drawPartialOverflowTabs();

  abstract static class Horizontal extends SingleRowLayoutStrategy {
    protected Horizontal(final SingleRowLayout layout) {
      super(layout);
    }

    @Override
    public boolean isToCenterTextWhenStretched() {
      return true;
    }

    @Override
    public boolean isDragOut(TabLabel tabLabel, int deltaX, int deltaY) {
      Rectangle bounds = tabLabel.getBounds();
      if (bounds.x + bounds.width + deltaX < 0 || bounds.x + bounds.width > tabLabel.getParent().getWidth()) return true;
      return Math.abs(deltaY) > tabLabel.getHeight() * TabLayout.getDragOutMultiplier();
    }

    @Override
    public @NotNull Point adjustDropPoint(@NotNull Point point) {
      var layout = myLayout.lastSingRowLayout;
      if (layout == null) return point;
      var rowRect = layout.getHeaderRectangle();
      var dropTolerance = JBUI.scale(TabsUtil.UNSCALED_DROP_TOLERANCE);
      var adjustedPoint = new Point(point);
      // Allow the tab to be dragged above or below the tab row without detaching,
      // as long as the tab has not been dragged further away than the tab row height.
      if (adjustedPoint.y >= rowRect.y - dropTolerance && adjustedPoint.y <= rowRect.y + rowRect.height + dropTolerance) {
        adjustedPoint.y = rowRect.y + rowRect.height / 2; // pretend the mouse is centered vertically and not moved away at all
      }
      return adjustedPoint;
    }

    @Override
    public int getMoreRectAxisSize() {
      return myTabs.getMoreToolbarPreferredSize().width;
    }

    @Override
    public int getEntryPointAxisSize() {
      return myTabs.getEntryPointPreferredSize().width;
    }

    @Override
    public int getToFitLength(final SingleRowPassInfo data) {
      int length = myTabs.getWidth() - data.insets.left - data.insets.right;
      JComponent hToolbar = data.hToolbar.get();
      if (hToolbar != null) {
        length -= hToolbar.getMinimumSize().width;
      }
      JComponent hfToolbar = data.hfToolbar.get();
      if (hfToolbar != null) {
        length -= hfToolbar.getPreferredSize().width;
      }
      length += getStartPosition(data);
      int entryPointWidth = myTabs.getEntryPointPreferredSize().width;
      Insets toolbarInsets = myTabs.getActionsInsets();
      int insets = toolbarInsets.left + toolbarInsets.right;
      length -= (entryPointWidth + insets * Math.signum(entryPointWidth));
      return length;
    }

    @Override
    public int getLengthIncrement(final Dimension labelPrefSize) {
      return myTabs.isEditorTabs() ? Math.max(labelPrefSize.width, MIN_TAB_WIDTH) : labelPrefSize.width;
    }

    @Override
    public int getAdditionalLength(SingleRowPassInfo data) {
      JComponent hfToolbar = data.hfToolbar.get();
      return hfToolbar == null ? 0 : hfToolbar.getPreferredSize().width;
    }

    @Override
    public int getMinPosition(Rectangle bounds) {
      return (int)bounds.getX();
    }

    @Override
    public int getMaxPosition(final Rectangle bounds) {
      return (int)bounds.getMaxX();
    }

    @Override
    public int getFixedFitLength(final SingleRowPassInfo data) {
      return myTabs.getHeaderFitSize().height;
    }

    @Override
    public Rectangle getLayoutRec(final SingleRowPassInfo data,
                                  final int position,
                                  final int fixedPos,
                                  final int length,
                                  final int fixedFitLength) {
      return new Rectangle(position, fixedPos, length, fixedFitLength);
    }

    @Override
    public int getStartPosition(final SingleRowPassInfo data) {
      JComponent hfToolbar = data.hfToolbar.get();
      return hfToolbar == null ? data.insets.left : data.insets.left + hfToolbar.getPreferredSize().width;
    }

    @Override
    public boolean drawPartialOverflowTabs() {
      return true;
    }
  }

  static class Top extends Horizontal {

    Top(final SingleRowLayout layout) {
      super(layout);
    }

    @Override
    public boolean isSideComponentOnTabs() {
      return myTabs.getHorizontalSide() && myTabs.isSideComponentOnTabs();
    }

    @Override
    public ShapeTransform createShapeTransform(Rectangle labelRec) {
      return new ShapeTransform.Top(labelRec);
    }

    @Override
    public int getFixedPosition(final SingleRowPassInfo data) {
      return data.insets.top;
    }

    @Override
    public Rectangle getEntryPointRect(SingleRowPassInfo data) {
      int x;
      if (myTabs.isEditorTabs()) {
        x = data.layoutSize.width - myTabs.getActionsInsets().right - data.entryPointAxisSize;
      }
      else {
        x = data.position;
      }
      return new Rectangle(x, 1, data.entryPointAxisSize, myTabs.getHeaderFitSize().height);
    }

    @Override
    public Rectangle getMoreRect(final SingleRowPassInfo data) {
      int x;
      if (myTabs.isEditorTabs()) {
        x = data.layoutSize.width - myTabs.getActionsInsets().right - data.moreRectAxisSize;
      }
      else {
        x = data.position;
      }
      x -= data.entryPointAxisSize;
      return new Rectangle(x, 1, data.moreRectAxisSize, myTabs.getHeaderFitSize().height);
    }

    @Override
    protected Rectangle getTitleRect(SingleRowPassInfo data) {
      return new Rectangle(0, 0, myTabs.getTitleWrapper().getPreferredSize().width, myTabs.getHeaderFitSize().height);
    }

    @Override
    public void layoutComp(SingleRowPassInfo data) {
      if (myTabs.isHideTabs()) {
        myTabs.layoutComp(data, 0, 0, 0, 0);
      }
      else {
        JComponent vToolbar = data.vToolbar.get();
        final int vToolbarWidth = vToolbar != null ? vToolbar.getPreferredSize().width : 0;
        final int vSeparatorWidth = vToolbarWidth > 0 ? myTabs.separatorWidth : 0;
        final int x = vToolbarWidth > 0 ? vToolbarWidth + vSeparatorWidth : 0;
        JComponent hToolbar = data.hToolbar.get();
        JComponent hfToolbar = data.hfToolbar.get();
        final int hToolbarHeight = !myTabs.isSideComponentOnTabs() && hToolbar != null ? hToolbar.getPreferredSize().height : 0;
        final int y = myTabs.getHeaderFitSize().height +
                      (Math.max(hToolbarHeight, 0));

        JComponent comp = data.component.get();
        if (hfToolbar != null && myTabs.isSideComponentOnTabs()) {
          final Rectangle rec = new Rectangle(data.insets.left, data.insets.top,
                                              hfToolbar.getPreferredSize().width,
                                              // reduce toolbar height by 1 pixel to properly paint the border between tabs and the content
                                              myTabs.getHeaderFitSize().height - JBUI.scale(1));
          myTabs.layout(hfToolbar, rec);
        }
        if (hToolbar != null) {
          final Rectangle compBounds = myTabs.layoutComp(x, y, comp, 0, 0);
          if (myTabs.isSideComponentOnTabs()) {
            int toolbarX = (!data.moreRect.isEmpty() ? (int)data.moreRect.getMaxX() : data.position) + myTabs.getToolbarInset();
            final Rectangle rec = new Rectangle(toolbarX, data.insets.top,
                                                myTabs.getSize().width - data.insets.left - toolbarX,
                                                // reduce toolbar height by 1 pixel to properly paint the border between tabs and the content
                                                myTabs.getHeaderFitSize().height - JBUI.scale(1));
            myTabs.layout(hToolbar, rec);
          }
          else {
            final int toolbarHeight = hToolbar.getPreferredSize().height;
            myTabs.layout(hToolbar, compBounds.x, compBounds.y - toolbarHeight, compBounds.width, toolbarHeight);
          }
        }
        else if (vToolbar != null) {
          if (myTabs.isSideComponentBefore()) {
            final Rectangle compBounds = myTabs.layoutComp(x, y, comp, 0, 0);
            myTabs.layout(vToolbar, compBounds.x - vToolbarWidth - vSeparatorWidth, compBounds.y, vToolbarWidth, compBounds.height);
          }
          else {
            int width = vToolbarWidth > 0 ? myTabs.getWidth() - vToolbarWidth - vSeparatorWidth : myTabs.getWidth();
            final Rectangle compBounds = myTabs.layoutComp(new Rectangle(0, y, width, myTabs.getHeight()), comp, 0, 0);
            myTabs.layout(vToolbar, compBounds.x + compBounds.width + vSeparatorWidth, compBounds.y, vToolbarWidth, compBounds.height);
          }
        }
        else {
          myTabs.layoutComp(x, y, comp, 0, 0);
        }
      }
    }
  }

  static final class Bottom extends Horizontal {
    Bottom(final SingleRowLayout layout) {
      super(layout);
    }

    @Override
    public void layoutComp(SingleRowPassInfo data) {
      if (myTabs.isHideTabs()) {
        myTabs.layoutComp(data, 0, 0, 0, 0);
      }
      else {
        myTabs.layoutComp(data, 0, 0, 0, -(myTabs.getHeaderFitSize().height));
      }
    }

    @Override
    public int getFixedPosition(final SingleRowPassInfo data) {
      return myTabs.getSize().height - data.insets.bottom - myTabs.getHeaderFitSize().height;
    }

    @Override
    public Rectangle getEntryPointRect(SingleRowPassInfo data) {
      int x;
      if (myTabs.isEditorTabs()) {
        x = data.layoutSize.width - myTabs.getActionsInsets().right - data.entryPointAxisSize;
      }
      else {
        x = data.position;
      }
      return new Rectangle(x, getFixedPosition(data), data.entryPointAxisSize, myTabs.getHeaderFitSize().height);
    }

    @Override
    public Rectangle getMoreRect(final SingleRowPassInfo data) {
      int x;
      if (myTabs.isEditorTabs()) {
        x = data.layoutSize.width - myTabs.getActionsInsets().right - data.moreRectAxisSize;
      }
      else {
        x = data.position;
      }
      x -= data.entryPointAxisSize;
      return new Rectangle(x, getFixedPosition(data), data.moreRectAxisSize, myTabs.getHeaderFitSize().height);
    }

    @Override
    protected Rectangle getTitleRect(SingleRowPassInfo data) {
      return new Rectangle(0, getFixedPosition(data), myTabs.getTitleWrapper().getPreferredSize().width, myTabs.getHeaderFitSize().height);
    }

    @Override
    public ShapeTransform createShapeTransform(Rectangle labelRec) {
      return new ShapeTransform.Bottom(labelRec);
    }
  }

  abstract static class Vertical extends SingleRowLayoutStrategy {
    protected Vertical(SingleRowLayout layout) {
      super(layout);
    }

    @Override
    public boolean isDragOut(TabLabel tabLabel, int deltaX, int deltaY) {
      Rectangle bounds = tabLabel.getBounds();
      if (bounds.y + bounds.height + deltaX < 0 || bounds.y + bounds.height > tabLabel.getParent().getHeight()) return true;
      return Math.abs(deltaX) > tabLabel.getWidth() * TabLayout.getDragOutMultiplier();
    }

    @Override
    public boolean isToCenterTextWhenStretched() {
      return false;
    }

    @Override
    int getEntryPointAxisSize() {
      return myTabs.getEntryPointPreferredSize().height;
    }

    @Override
    int getMoreRectAxisSize() {
      return myTabs.getMoreToolbarPreferredSize().height;
    }

    @Override
    public int getStartPosition(final SingleRowPassInfo data) {
      return data.insets.top;
    }

    @Override
    public int getToFitLength(final SingleRowPassInfo data) {
      int length = myTabs.getHeight() - data.insets.top - data.insets.bottom;
      if (!ExperimentalUI.isNewUI()) {
        int entryPointHeight = data.entryPointAxisSize;
        Insets toolbarInsets = myTabs.getActionsInsets();
        int insets = toolbarInsets.top + toolbarInsets.bottom;
        length -= (entryPointHeight + insets * Math.signum(entryPointHeight));
      }
      return length;
    }

    @Override
    public int getLengthIncrement(final Dimension labelPrefSize) {
      return labelPrefSize.height;
    }

    @Override
    public int getAdditionalLength(SingleRowPassInfo data) {
      return ExperimentalUI.isNewUI() ? JBUI.scale(32) : 0;
    }

    @Override
    public int getMinPosition(Rectangle bounds) {
      return (int)bounds.getMinY();
    }

    @Override
    public int getMaxPosition(final Rectangle bounds) {
      return (int)bounds.getMaxY();
    }

    @Override
    public int getFixedFitLength(final SingleRowPassInfo data) {
      return myTabs.getHeaderFitSize().width;
    }

    @Override
    public Rectangle getLayoutRec(final SingleRowPassInfo data,
                                  final int position,
                                  final int fixedPos,
                                  final int length,
                                  final int fixedFitLength) {
      Rectangle baseRect = new Rectangle(fixedPos, position, fixedFitLength, length);
      if (!ExperimentalUI.isNewUI()) {
        return baseRect;
      }
      Rectangle rect = myTabs.getMoreToolbar().getComponent().isVisible() && !data.moreRect.isEmpty()
                       ? data.moreRect : data.entryPointRect;
      Rectangle leftmostButtonRect = new Rectangle(rect.x - myTabs.getActionsInsets().left, rect.y, rect.width, rect.height);
      Rectangle intersection = baseRect.intersection(leftmostButtonRect);
      if (intersection.height > length * 0.4) {
        return new Rectangle(baseRect.x, baseRect.y, leftmostButtonRect.x - baseRect.x, baseRect.height);
      }
      return baseRect;
    }

    @Override
    public boolean drawPartialOverflowTabs() {
      return ExperimentalUI.isNewUI();
    }
  }

  static class Left extends Vertical {
    Left(final SingleRowLayout layout) {
      super(layout);
    }


    @Override
    public void layoutComp(SingleRowPassInfo data) {
      if (myTabs.isHideTabs()) {
        myTabs.layoutComp(data, 0, 0, 0, 0);
      }
      else {
        myTabs.layoutComp(data, myTabs.getHeaderFitSize().width, 0, 0, 0);
      }
    }

    @Override
    public ShapeTransform createShapeTransform(Rectangle labelRec) {
      return new ShapeTransform.Left(labelRec);
    }

    @Override
    protected Rectangle getTitleRect(SingleRowPassInfo data) {
      return new Rectangle(0, 0, myTabs.getHeaderFitSize().width, myTabs.getTitleWrapper().getPreferredSize().height);
    }

    @Override
    public int getFixedPosition(final SingleRowPassInfo data) {
      return data.insets.left;
    }

    @Override
    public Rectangle getEntryPointRect(SingleRowPassInfo data) {
      Insets insets = myTabs.getActionsInsets();
      Dimension entryPointSize = myTabs.getEntryPointPreferredSize();
      if (ExperimentalUI.isNewUI()) {
        return new Rectangle(myTabs.getHeaderFitSize().width - entryPointSize.width - data.insets.right - insets.right - 1,
                             myTabs.getHeight() - entryPointSize.height - data.insets.bottom - insets.bottom,
                             entryPointSize.width, entryPointSize.height);
      }
      else {
        return new Rectangle(data.insets.left + JBTabsImpl.getSelectionTabVShift(),
                             myTabs.getHeight() - entryPointSize.height - data.insets.bottom - insets.bottom,
                             myTabs.getHeaderFitSize().width, entryPointSize.height);
      }
    }

    @Override
    public Rectangle getMoreRect(final SingleRowPassInfo data) {
      Dimension entryPointSize = myTabs.getEntryPointPreferredSize();
      Dimension moreToolbarSize = myTabs.getMoreToolbarPreferredSize();
      Insets insets = myTabs.getActionsInsets();
      if (ExperimentalUI.isNewUI()) {
        return new Rectangle(
          myTabs.getHeaderFitSize().width - entryPointSize.width - moreToolbarSize.width - data.insets.right - insets.right - 1,
          myTabs.getHeight() - moreToolbarSize.height - data.insets.bottom - insets.bottom,
          moreToolbarSize.width, moreToolbarSize.height);
      }
      else {
        return new Rectangle(data.insets.left + JBTabsImpl.getSelectionTabVShift(),
                             myTabs.getHeight() -
                             moreToolbarSize.height -
                             entryPointSize.height -
                             data.insets.bottom -
                             insets.bottom -
                             insets.top,
                             myTabs.getHeaderFitSize().width, moreToolbarSize.height);
      }
    }
  }

  static class Right extends Vertical {
    Right(SingleRowLayout layout) {
      super(layout);
    }

    @Override
    public void layoutComp(SingleRowPassInfo data) {
      if (myTabs.isHideTabs()) {
        myTabs.layoutComp(data, 0, 0, 0, 0);
      }
      else {
        myTabs.layoutComp(data, 0, 0, -(myTabs.getHeaderFitSize().width), 0);
      }
    }

    @Override
    public ShapeTransform createShapeTransform(Rectangle labelRec) {
      return new ShapeTransform.Right(labelRec);
    }

    @Override
    public int getFixedPosition(SingleRowPassInfo data) {
      return data.layoutSize.width - myTabs.getHeaderFitSize().width - data.insets.right;
    }

    @Override
    public Rectangle getEntryPointRect(SingleRowPassInfo data) {
      Dimension entryPointSize = myTabs.getEntryPointPreferredSize();
      Insets insets = myTabs.getActionsInsets();
      if (ExperimentalUI.isNewUI()) {
        return new Rectangle(data.layoutSize.width - entryPointSize.width - insets.right,
                             myTabs.getHeight() - entryPointSize.height - data.insets.bottom - insets.bottom,
                             entryPointSize.width, entryPointSize.height);
      }
      else {
        return new Rectangle(data.layoutSize.width - myTabs.getHeaderFitSize().width,
                             myTabs.getHeight() - entryPointSize.height - data.insets.bottom - insets.bottom,
                             myTabs.getHeaderFitSize().width,
                             entryPointSize.height);
      }
    }

    @Override
    public Rectangle getMoreRect(SingleRowPassInfo data) {
      Dimension entryPointSize = myTabs.getEntryPointPreferredSize();
      Dimension moreToolbarSize = myTabs.getMoreToolbarPreferredSize();
      Insets insets = myTabs.getActionsInsets();
      if (ExperimentalUI.isNewUI()) {
        return new Rectangle(data.layoutSize.width - moreToolbarSize.width - entryPointSize.width - insets.right,
                             myTabs.getHeight() - moreToolbarSize.height - data.insets.bottom - insets.bottom,
                             moreToolbarSize.width, moreToolbarSize.height);
      }
      else {
        return new Rectangle(data.layoutSize.width - myTabs.getHeaderFitSize().width,
                             myTabs.getHeight() - moreToolbarSize.height - entryPointSize.height - data.insets.bottom - insets.bottom,
                             myTabs.getHeaderFitSize().width,
                             moreToolbarSize.height);
      }
    }

    @Override
    protected Rectangle getTitleRect(SingleRowPassInfo data) {
      return new Rectangle(data.layoutSize.width - myTabs.getHeaderFitSize().width,
                           0,
                           myTabs.getHeaderFitSize().width,
                           myTabs.getTitleWrapper().getPreferredSize().height);
    }
  }
}
