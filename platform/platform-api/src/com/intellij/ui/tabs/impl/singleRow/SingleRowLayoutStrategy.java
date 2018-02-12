/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ui.tabs.impl.singleRow;

import com.intellij.icons.AllIcons;
import com.intellij.ui.tabs.JBTabsPosition;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.ui.tabs.impl.ShapeTransform;
import com.intellij.ui.tabs.impl.TabLabel;
import com.intellij.ui.tabs.impl.TabLayout;

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

  public abstract Rectangle getMoreRect(final SingleRowPassInfo data);

  public abstract boolean isToCenterTextWhenStretched();

  public abstract ShapeTransform createShapeTransform(Rectangle rectangle);

  public abstract boolean canBeStretched();

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

    public boolean isToCenterTextWhenStretched() {
      return true;
    }

    @Override
    public boolean canBeStretched() {
      return true;
    }

    @Override
    public boolean isDragOut(TabLabel tabLabel, int deltaX, int deltaY) {
      return Math.abs(deltaY) > tabLabel.getHeight() * TabLayout.getDragOutMultiplier();
    }

    public int getMoreRectAxisSize() {
      return AllIcons.General.MoreTabs.getIconWidth() + 15;
    }

    public int getToFitLength(final SingleRowPassInfo data) {
      JComponent hToolbar = data.hToolbar.get();
      if (hToolbar != null) {
        return myTabs.getWidth() - data.insets.left - data.insets.right - hToolbar.getMinimumSize().width;
      } else {
        return myTabs.getWidth() - data.insets.left - data.insets.right;
      }
    }

    public int getLengthIncrement(final Dimension labelPrefSize) {
      return myTabs.isEditorTabs() ? labelPrefSize.width < MIN_TAB_WIDTH ? MIN_TAB_WIDTH : labelPrefSize.width : labelPrefSize.width;
    }

    @Override
    public int getMinPosition(Rectangle bounds) {
      return (int)bounds.getX();
    }

    public int getMaxPosition(final Rectangle bounds) {
      return (int)bounds.getMaxX();
    }

    public int getFixedFitLength(final SingleRowPassInfo data) {
      return myTabs.myHeaderFitSize.height;
    }

    public Rectangle getLayoutRec(final int position, final int fixedPos, final int length, final int fixedFitLength) {
      return new Rectangle(position, fixedPos + (myTabs.getTabsPosition() == JBTabsPosition.bottom ? 1 : 0), length, fixedFitLength);
    }

    public int getStartPosition(final SingleRowPassInfo data) {
      return data.insets.left;
    }

    @Override
    public boolean drawPartialOverflowTabs() {
      return false;
    }

    @Override
    public int getScrollUnitIncrement(TabLabel label) {
      return 10;
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

    public ShapeTransform createShapeTransform(Rectangle labelRec) {
      return new ShapeTransform.Top(labelRec);
    }

    public int getFixedPosition(final SingleRowPassInfo data) {
      return data.insets.top;
    }

    public Rectangle getMoreRect(final SingleRowPassInfo data) {
      int x;
      if (myTabs.isEditorTabs()) {
        x = data.layoutSize.width - data.moreRectAxisSize - 1;
      }
      else {
        x = data.position + (data.lastGhostVisible ? data.lastGhost.width : 0);
      }
      return new Rectangle(x, data.insets.top + JBTabsImpl.getSelectionTabVShift(),
                                            data.moreRectAxisSize - 1, myTabs.myHeaderFitSize.height - 1);
    }


    @Override
    public void layoutComp(SingleRowPassInfo data) {
      if (myTabs.isHideTabs()) {
        myTabs.layoutComp(data, 0, 0, 0, 0);
      } else {
        JComponent vToolbar = data.vToolbar.get();
        final int vToolbarWidth = vToolbar != null ? vToolbar.getPreferredSize().width : 0;
        final int vSeparatorWidth = vToolbarWidth > 0 ? 1 : 0;
        final int x = vToolbarWidth > 0 ? vToolbarWidth + vSeparatorWidth : 0;
        JComponent hToolbar = data.hToolbar.get();
        final int hToolbarHeight = !myTabs.isSideComponentOnTabs() && hToolbar != null ? hToolbar.getPreferredSize().height : 0;
        final int y = myTabs.myHeaderFitSize.height + (myTabs.isEditorTabs() ? 0 : 1) +
                      (hToolbarHeight > 0 ? hToolbarHeight - 2 : 0);

        JComponent comp = data.comp.get();
        if (hToolbar != null) {
          final Rectangle compBounds = myTabs.layoutComp(x, y, comp, 0, 0);
          if (myTabs.isSideComponentOnTabs()) {
            int toolbarX = (data.moreRect != null ? (int)data.moreRect.getMaxX() : data.position) + myTabs.getToolbarInset();
            final Rectangle rec =
              new Rectangle(toolbarX, data.insets.top + 1, myTabs.getSize().width - data.insets.left - toolbarX, myTabs.myHeaderFitSize.height);
            myTabs.layout(hToolbar, rec);
          } else {
            final int toolbarHeight = hToolbar.getPreferredSize().height - 2;
            myTabs.layout(hToolbar, compBounds.x, compBounds.y - toolbarHeight - 1, compBounds.width, toolbarHeight);
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
        myTabs.layoutComp(data, 0, 0, 0, -(myTabs.myHeaderFitSize.height + 1));
      }
    }

    public int getFixedPosition(final SingleRowPassInfo data) {
      return myTabs.getSize().height - data.insets.bottom - myTabs.myHeaderFitSize.height - 1;
    }

    public Rectangle getMoreRect(final SingleRowPassInfo data) {
      return new Rectangle(myTabs.getWidth() - data.insets.right - data.moreRectAxisSize + 2, getFixedPosition(data),
                                            data.moreRectAxisSize - 1, myTabs.myHeaderFitSize.height - 1);
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
      return Math.abs(deltaX) > tabLabel.getHeight() * TabLayout.getDragOutMultiplier();
    }

    public boolean isToCenterTextWhenStretched() {
      return false;
    }

    int getMoreRectAxisSize() {
      return AllIcons.General.MoreTabs.getIconHeight() + 4;
    }

    @Override
    public boolean canBeStretched() {
      return false;
    }

    public int getStartPosition(final SingleRowPassInfo data) {
      return data.insets.top;
    }

    public int getToFitLength(final SingleRowPassInfo data) {
      return myTabs.getHeight() - data.insets.top - data.insets.bottom;
    }

    public int getLengthIncrement(final Dimension labelPrefSize) {
      return labelPrefSize.height;
    }

    @Override
    public int getMinPosition(Rectangle bounds) {
      return (int) bounds.getMinY();
    }

    public int getMaxPosition(final Rectangle bounds) {
      int maxY = (int)bounds.getMaxY();
      return myTabs.isEditorTabs() ? maxY - 1 : maxY;
    }

    public int getFixedFitLength(final SingleRowPassInfo data) {
      return myTabs.myHeaderFitSize.width;
    }

    @Override
    public boolean drawPartialOverflowTabs() {
      return false;
    }

    @Override
    public int getScrollUnitIncrement(TabLabel label) {
      return label.getPreferredSize().height;
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
        myTabs.layoutComp(data, myTabs.myHeaderFitSize.width + 1, 0, 0, 0);
      }
    }

    @Override
    public ShapeTransform createShapeTransform(Rectangle labelRec) {
      return new ShapeTransform.Left(labelRec);
    }

    public Rectangle getLayoutRec(final int position, final int fixedPos, final int length, final int fixedFitLength) {
      return new Rectangle(fixedPos, position, fixedFitLength, length);
    }

    public int getFixedPosition(final SingleRowPassInfo data) {
      return data.insets.left;
    }

    public Rectangle getMoreRect(final SingleRowPassInfo data) {
      return new Rectangle(data.insets.left + JBTabsImpl.getSelectionTabVShift(),
                           myTabs.getHeight() - data.insets.bottom - data.moreRectAxisSize - 1,
                           myTabs.myHeaderFitSize.width - 1,
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

    public ShapeTransform createShapeTransform(Rectangle labelRec) {
      return new ShapeTransform.Right(labelRec);
    }

    public Rectangle getLayoutRec(int position, int fixedPos, int length, int fixedFitLength) {
      return new Rectangle(fixedPos, position, fixedFitLength - 1, length);
    }

    public int getFixedPosition(SingleRowPassInfo data) {
      return data.layoutSize.width - myTabs.myHeaderFitSize.width - data.insets.right;
    }

    public Rectangle getMoreRect(SingleRowPassInfo data) {
      return new Rectangle(data.layoutSize.width - myTabs.myHeaderFitSize.width,
                        myTabs.getHeight() - data.insets.bottom - data.moreRectAxisSize - 1,
                        myTabs.myHeaderFitSize.width - 1,
                        data.moreRectAxisSize - 1);
    }
  }

}
