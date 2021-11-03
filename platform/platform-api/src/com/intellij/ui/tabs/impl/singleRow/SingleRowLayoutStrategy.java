// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.impl.singleRow;

import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.ui.tabs.impl.ShapeTransform;
import com.intellij.ui.tabs.impl.TabLabel;
import com.intellij.ui.tabs.impl.TabLayout;
import com.intellij.util.MathUtil;

import javax.swing.*;
import java.awt.*;

public abstract class SingleRowLayoutStrategy {

  private static final int MIN_TAB_WIDTH = 50;
  final SingleRowLayout myLayout;
  final JBTabsImpl myTabs;

  protected SingleRowLayoutStrategy(final SingleRowLayout layout) {
    myLayout = layout;
    myTabs = myLayout.myTabs;
  }

  abstract int getMoreRectAxisSize();

  abstract int getEntryPointAxisSize();

  public abstract int getStartPosition(final SingleRowPassInfo data);

  public abstract int getToFitLength(final SingleRowPassInfo data);

  public abstract int getLengthIncrement(final Dimension dimension);

  public abstract int getMinPosition(final Rectangle bounds);

  public abstract int getMaxPosition(final Rectangle bounds);

  protected abstract int getFixedFitLength(final SingleRowPassInfo data);

  public Rectangle getLayoutRect(final SingleRowPassInfo data, final int position, final int length) {
    return getLayoutRec(position, getFixedPosition(data), length, getFixedFitLength(data));
  }

  protected abstract Rectangle getLayoutRec(final int position, final int fixedPos, final int length, final int fixedFitLength);

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

  /**
   * Whether a tab that didn't fit completely on the right/bottom side in scrollable layout should be clipped or hidden altogether.
   *
   * @return true if the tab should be clipped, false if hidden.
   */
  public abstract boolean drawPartialOverflowTabs();

  /**
   * Return the change of scroll offset for every unit of mouse wheel scrolling.
   *
   * @param label the first visible tab label
   * @return the scroll amount
   */
  public abstract int getScrollUnitIncrement(TabLabel label);

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
    public int getMoreRectAxisSize() {
      return ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.width + 6;
    }

    @Override
    public int getEntryPointAxisSize() {
      return myTabs.getEntryPointPreferredSize().width;
    }

    @Override
    public int getToFitLength(final SingleRowPassInfo data) {
      JComponent hToolbar = data.hToolbar.get();
      int length;
      if (hToolbar != null) {
        length = myTabs.getWidth() - data.insets.left - data.insets.right - hToolbar.getMinimumSize().width;
      } else {
        length = myTabs.getWidth() - data.insets.left - data.insets.right;
      }
      int entryPointWidth = myTabs.getEntryPointPreferredSize().width;
      length -= (entryPointWidth + 2 * Math.signum(entryPointWidth));
      return length;
    }

    @Override
    public int getLengthIncrement(final Dimension labelPrefSize) {
      return myTabs.isEditorTabs() ? Math.max(labelPrefSize.width, MIN_TAB_WIDTH) : labelPrefSize.width;
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
      return myTabs.myHeaderFitSize.height;
    }

    @Override
    public Rectangle getLayoutRec(final int position, final int fixedPos, final int length, final int fixedFitLength) {
      return new Rectangle(position, fixedPos, length, fixedFitLength);
    }

    @Override
    public int getStartPosition(final SingleRowPassInfo data) {
      return data.insets.left;
    }

    @Override
    public boolean drawPartialOverflowTabs() {
      return true;
    }

    @Override
    public int getScrollUnitIncrement(TabLabel label) {
      return MathUtil.clamp(Registry.intValue("ide.editor.tabs.scroll.unit.increment", 10), 1, 200);
    }
  }

  static class Top extends Horizontal {

    Top(final SingleRowLayout layout) {
      super(layout);
    }

    @Override
    public boolean isSideComponentOnTabs() {
      return !myTabs.isSideComponentVertical() && myTabs.isSideComponentOnTabs();
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
        x = data.layoutSize.width - data.entryPointAxisSize - 1;
      }
      else {
        x = data.position;
      }
      return new Rectangle(x, 1, data.entryPointAxisSize - 1, myTabs.myHeaderFitSize.height);
    }

    @Override
    public Rectangle getMoreRect(final SingleRowPassInfo data) {
      int x;
      if (myTabs.isEditorTabs()) {
        x = data.layoutSize.width - data.moreRectAxisSize - 1;
      }
      else {
        x = data.position;
      }
      x -= data.entryPointAxisSize;
      return new Rectangle(x, 1, data.moreRectAxisSize - 1, myTabs.myHeaderFitSize.height);
    }

    @Override
    protected Rectangle getTitleRect(SingleRowPassInfo data) {
      return new Rectangle(0, 0, myTabs.myTitleWrapper.getPreferredSize().width, myTabs.myHeaderFitSize.height);
    }

    @Override
    public void layoutComp(SingleRowPassInfo data) {
      if (myTabs.isHideTabs()) {
        myTabs.layoutComp(data, 0, 0, 0, 0);
      } else {
        JComponent vToolbar = data.vToolbar.get();
        final int vToolbarWidth = vToolbar != null ? vToolbar.getPreferredSize().width : 0;
        final int vSeparatorWidth = vToolbarWidth > 0 ? myTabs.getSeparatorWidth() : 0;
        final int x = vToolbarWidth > 0 ? vToolbarWidth + vSeparatorWidth : 0;
        JComponent hToolbar = data.hToolbar.get();
        final int hToolbarHeight = !myTabs.isSideComponentOnTabs() && hToolbar != null ? hToolbar.getPreferredSize().height : 0;
        final int y = myTabs.myHeaderFitSize.height +
                      (Math.max(hToolbarHeight, 0));

        JComponent comp = data.comp.get();
        if (hToolbar != null) {
          final Rectangle compBounds = myTabs.layoutComp(x, y, comp, 0, 0);
          if (myTabs.isSideComponentOnTabs()) {
            int toolbarX = (data.moreRect != null ? (int)data.moreRect.getMaxX() : data.position) + myTabs.getToolbarInset();
            final Rectangle rec =
              new Rectangle(toolbarX, data.insets.top, myTabs.getSize().width - data.insets.left - toolbarX, myTabs.myHeaderFitSize.height);
            myTabs.layout(hToolbar, rec);
          } else {
            final int toolbarHeight = hToolbar.getPreferredSize().height;
            myTabs.layout(hToolbar, compBounds.x, compBounds.y - toolbarHeight, compBounds.width, toolbarHeight);
          }
        } else if (vToolbar != null) {
          if (myTabs.isSideComponentBefore()) {
            final Rectangle compBounds = myTabs.layoutComp(x, y, comp, 0, 0);
            myTabs.layout(vToolbar, compBounds.x - vToolbarWidth - vSeparatorWidth, compBounds.y, vToolbarWidth, compBounds.height);
          } else {
            int width = vToolbarWidth > 0 ? myTabs.getWidth() - vToolbarWidth - vSeparatorWidth : myTabs.getWidth();
            final Rectangle compBounds = myTabs.layoutComp(new Rectangle(0, y, width, myTabs.getHeight()), comp, 0, 0);
            myTabs.layout(vToolbar, compBounds.x + compBounds.width + vSeparatorWidth, compBounds.y, vToolbarWidth, compBounds.height);
          }
        } else {
          myTabs.layoutComp(x, y, comp, 0, 0);
        }
      }
    }
  }

  static class Bottom extends Horizontal {
    Bottom(final SingleRowLayout layout) {
      super(layout);
    }

    @Override
    public void layoutComp(SingleRowPassInfo data) {
      if (myTabs.isHideTabs()) {
        myTabs.layoutComp(data, 0, 0, 0, 0);
      } else {
        myTabs.layoutComp(data, 0, 0, 0, -(myTabs.myHeaderFitSize.height));
      }
    }

    @Override
    public int getFixedPosition(final SingleRowPassInfo data) {
      return myTabs.getSize().height - data.insets.bottom - myTabs.myHeaderFitSize.height;
    }

    @Override
    public Rectangle getEntryPointRect(SingleRowPassInfo data) {
      int x;
      if (myTabs.isEditorTabs()) {
        x = data.layoutSize.width - data.entryPointAxisSize - 1;
      }
      else {
        x = data.position;
      }
      return new Rectangle(x, getFixedPosition(data),
                           data.entryPointAxisSize - 1, myTabs.myHeaderFitSize.height);
    }

    @Override
    public Rectangle getMoreRect(final SingleRowPassInfo data) {
      int x;
      if (myTabs.isEditorTabs()) {
        x = data.layoutSize.width - data.moreRectAxisSize - 1;
      }
      else {
        x = data.position;
      }
      x -= data.entryPointAxisSize;
      return new Rectangle(x, getFixedPosition(data),
                           data.moreRectAxisSize - 1, myTabs.myHeaderFitSize.height);
    }

    @Override
    protected Rectangle getTitleRect(SingleRowPassInfo data) {
      return new Rectangle(0, getFixedPosition(data), myTabs.myTitleWrapper.getPreferredSize().width, myTabs.myHeaderFitSize.height);
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
      return ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.width + 2;
    }

    @Override
    public int getStartPosition(final SingleRowPassInfo data) {
      return data.insets.top;
    }

    @Override
    public int getToFitLength(final SingleRowPassInfo data) {
      int length = myTabs.getHeight() - data.insets.top - data.insets.bottom;
      int entryPointHeight = myTabs.getEntryPointPreferredSize().height;
      length -= (entryPointHeight + 2 * Math.signum(entryPointHeight));
      return length;
    }

    @Override
    public int getLengthIncrement(final Dimension labelPrefSize) {
      return labelPrefSize.height;
    }

    @Override
    public int getMinPosition(Rectangle bounds) {
      return (int) bounds.getMinY();
    }

    @Override
    public int getMaxPosition(final Rectangle bounds) {
      return (int)bounds.getMaxY();
    }

    @Override
    public int getFixedFitLength(final SingleRowPassInfo data) {
      return myTabs.myHeaderFitSize.width;
    }

    @Override
    public boolean drawPartialOverflowTabs() {
      return false;
    }

    @Override
    public int getScrollUnitIncrement(TabLabel label) {
      return (label.getPreferredSize().height + myTabs.getTabHGap());
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
      } else {
        myTabs.layoutComp(data, myTabs.myHeaderFitSize.width, 0, 0, 0);
      }
    }

    @Override
    public ShapeTransform createShapeTransform(Rectangle labelRec) {
      return new ShapeTransform.Left(labelRec);
    }

    @Override
    public Rectangle getLayoutRec(final int position, final int fixedPos, final int length, final int fixedFitLength) {
      return new Rectangle(fixedPos, position, fixedFitLength, length);
    }
    @Override
    protected Rectangle getTitleRect(SingleRowPassInfo data) {
      return new Rectangle(0, 0, myTabs.myHeaderFitSize.width, myTabs.myTitleWrapper.getPreferredSize().height);
    }

    @Override
    public int getFixedPosition(final SingleRowPassInfo data) {
      return data.insets.left;
    }

    @Override
    public Rectangle getEntryPointRect(SingleRowPassInfo data) {
      return new Rectangle(data.insets.left + JBTabsImpl.getSelectionTabVShift(),
                           myTabs.getHeight() - data.insets.bottom - data.entryPointAxisSize - 1,
                           myTabs.myHeaderFitSize.width,
                           data.entryPointAxisSize - 1);
    }

    @Override
    public Rectangle getMoreRect(final SingleRowPassInfo data) {
      return new Rectangle(data.insets.left + JBTabsImpl.getSelectionTabVShift(),
                           myTabs.getHeight() - data.insets.bottom - data.moreRectAxisSize - 1 - data.entryPointAxisSize,
                           myTabs.myHeaderFitSize.width,
                           data.moreRectAxisSize - 1);
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
      } else {
        myTabs.layoutComp(data, 0, 0, -(myTabs.myHeaderFitSize.width), 0);
      }
    }

    @Override
    public ShapeTransform createShapeTransform(Rectangle labelRec) {
      return new ShapeTransform.Right(labelRec);
    }

    @Override
    public Rectangle getLayoutRec(int position, int fixedPos, int length, int fixedFitLength) {
      return new Rectangle(fixedPos, position, fixedFitLength - 1, length);
    }

    @Override
    public int getFixedPosition(SingleRowPassInfo data) {
      return data.layoutSize.width - myTabs.myHeaderFitSize.width - data.insets.right;
    }

    @Override
    public Rectangle getEntryPointRect(SingleRowPassInfo data) {
      return new Rectangle(data.layoutSize.width - myTabs.myHeaderFitSize.width,
                           myTabs.getHeight() - data.insets.bottom - data.entryPointAxisSize - 1,
                           myTabs.myHeaderFitSize.width,
                           data.entryPointAxisSize - 1);
    }

    @Override
    public Rectangle getMoreRect(SingleRowPassInfo data) {
      return new Rectangle(data.layoutSize.width - myTabs.myHeaderFitSize.width,
                           myTabs.getHeight() - data.insets.bottom - data.moreRectAxisSize - 1 - data.entryPointAxisSize,
                           myTabs.myHeaderFitSize.width,
                           data.moreRectAxisSize - 1);
    }
    @Override
    protected Rectangle getTitleRect(SingleRowPassInfo data) {
      return new Rectangle(data.layoutSize.width - myTabs.myHeaderFitSize.width,
                           0,
                           myTabs.myHeaderFitSize.width,
                           myTabs.myTitleWrapper.getPreferredSize().height);
    }
  }

}
