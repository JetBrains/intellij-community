/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ui.popup;

import com.intellij.codeInsight.lookup.LookupAdapter;
import com.intellij.codeInsight.lookup.LookupEvent;
import com.intellij.codeInsight.lookup.LookupEx;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.DimensionService;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * @author pegov
 * @author Konstantin Bulenkov
 */
public class PopupPositionManager {
  private PopupPositionManager() {
  }

  public enum Position {
    TOP, BOTTOM, LEFT, RIGHT
  }

  public static void positionPopupInBestPosition(final JBPopup hint,
                                                 @Nullable final Editor editor,
                                                 @Nullable DataContext dataContext) {
    final LookupEx lookup = LookupManager.getActiveLookup(editor);
    if (lookup != null && lookup.getCurrentItem() != null && lookup.getComponent().isShowing()) {
      new PositionAdjuster(lookup.getComponent()).adjust(hint);
      lookup.addLookupListener(new LookupAdapter() {
        @Override
        public void lookupCanceled(LookupEvent event) {
          if (hint.isVisible()) {
            hint.cancel();
          }
        }
      });
      return;
    }

    final PositionAdjuster positionAdjuster = createPositionAdjuster();
    if (positionAdjuster != null) {
      positionAdjuster.adjust(hint);
      return;
    }

    if (editor != null && editor.getComponent().isShowing()) {
      hint.showInBestPositionFor(editor);
      return;
    }

    if (dataContext != null) {
      hint.showInBestPositionFor(dataContext);
    }
  }

  private static Component discoverPopup(final DataKey<JBPopup> datakey, Component focusOwner) {
    if (focusOwner == null) {
      focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    }

    if (focusOwner == null) return null;

    final DataContext dataContext = DataManager.getInstance().getDataContext(focusOwner);
    if (dataContext == null) return null;

    final JBPopup popup = datakey.getData(dataContext);
    if (popup != null && popup.isVisible()) {
      return popup.getContent();
    }

    return null;
  }

  @Nullable
  private static PositionAdjuster createPositionAdjuster() {
    final Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    if (focusOwner == null) return null;

    if (focusOwner instanceof JBList) {
      return new PositionAdjuster(SwingUtilities.getWindowAncestor(focusOwner));
    }

    final Component existing = discoverPopup(LangDataKeys.POSITION_ADJUSTER_POPUP, focusOwner);
    if (existing != null) {
      return new PositionAdjuster2(existing, discoverPopup(LangDataKeys.PARENT_POPUP, focusOwner));
    }

    //final Window window = SwingUtilities.getWindowAncestor(focusOwner);
    //return window == null ? null : new PositionAdjuster(window);
    return null;
  }

  private static class PositionAdjuster2 extends PositionAdjuster {

    private final Component myTopComponent;

    private PositionAdjuster2(final Component relativeTo, final Component topComponent) {
      super(relativeTo);
      myTopComponent = topComponent == null ? relativeTo : topComponent;
    }

    @Override
    protected int getYForTopPositioning() {
      return myTopComponent.getLocationOnScreen().y;
    }
  }

  public static class PositionAdjuster {
    private final int myGap;

    private final Component myRelativeTo;
    private final Point myRelativeOnScreen;
    private final Rectangle myScreenRect;

    public PositionAdjuster(final Component relativeTo, int gap) {
      myRelativeTo = relativeTo;
      myRelativeOnScreen = relativeTo.getLocationOnScreen();
      myScreenRect = ScreenUtil.getScreenRectangle(myRelativeOnScreen);
      myGap = gap;
    }

    public PositionAdjuster(final Component relativeTo) {
      this(relativeTo, 5);
    }

    protected Rectangle positionRight(final Dimension d) {
      return new Rectangle(myRelativeOnScreen.x + myRelativeTo.getWidth() + myGap, myRelativeOnScreen.y, d.width,
                           d.height);
    }

    protected Rectangle positionLeft(final Dimension d) {
      return new Rectangle(myRelativeOnScreen.x - myGap - d.width, myRelativeOnScreen.y, d.width, d.height);
    }

    protected Rectangle positionAbove(final Dimension d) {
      return new Rectangle(myRelativeOnScreen.x, getYForTopPositioning() - myGap - d.height, d.width, d.height);
    }

    protected Rectangle positionUnder(final Dimension d) {
      return new Rectangle(myRelativeOnScreen.x, myRelativeOnScreen.y + myGap + myRelativeTo.getHeight(), d.width, d.height);
    }

    protected int getYForTopPositioning() {
      return myRelativeOnScreen.y;
    }

    /**
     * Try to position:
     * 1. to the right
     * 2. to the left
     * 3. above
     * 4. under
     *
     * @param popup
     */
    public void adjust(final JBPopup popup) {
      adjust(popup, Position.RIGHT, Position.LEFT, Position.TOP, Position.BOTTOM);
    }

    public void adjust(final JBPopup popup, Position... traversalPolicy) {
      final Dimension d = getPopupSize(popup);

      Rectangle popupRect = null;
      Rectangle r = null;

      for (Position position : traversalPolicy) {
        switch (position) {
          case TOP:    r = positionAbove(d); break;
          case BOTTOM: r = positionUnder(d); break;
          case LEFT:   r = positionLeft(d);  break;
          case RIGHT:  r = positionRight(d); break;
        }
        if (myScreenRect.contains(r)) {
          popupRect = r;
          break;
        }
      }

      if (popupRect != null) {
        if (popup.isVisible()) {
          popup.setLocation(new Point(r.x, r.y));
        }
        else {
          final Point p = new Point(r.x - myRelativeOnScreen.x, r.y - myRelativeOnScreen.y);
          popup.show(new RelativePoint(myRelativeTo, p));
        }
      }
      else {
        // ok, popup does not fit, will try to resize it
        final java.util.List<Rectangle> boxes = new ArrayList<>();
        // right
        boxes.add(crop(myScreenRect, new Rectangle(myRelativeOnScreen.x + myRelativeTo.getWidth() + myGap, myRelativeOnScreen.y,
                                                   myScreenRect.width, myScreenRect.height)));

        // left
        boxes.add(crop(myScreenRect, new Rectangle(myScreenRect.x, myRelativeOnScreen.y, myRelativeOnScreen.x - myScreenRect.x - myGap,
                                                   myScreenRect.height)));

        // top
        boxes.add(crop(myScreenRect, new Rectangle(myRelativeOnScreen.x, myScreenRect.y,
                                                   myScreenRect.width, getYForTopPositioning() - myScreenRect.y - myGap)));

        // bottom
        boxes.add(crop(myScreenRect, new Rectangle(myRelativeOnScreen.x, myRelativeOnScreen.y + myRelativeTo.getHeight() + myGap,
                                                   myScreenRect.width, myScreenRect.height)));

        Collections.sort(boxes, (o1, o2) -> {
          final int i = new Integer(o1.width).compareTo(o2.width);
          return i == 0 ? new Integer(o1.height).compareTo(o2.height) : i;
        });

        final Rectangle suitableBox = boxes.get(boxes.size() - 1);
        final Rectangle crop = crop(suitableBox,
                                    new Rectangle(suitableBox.x < myRelativeOnScreen.x ? suitableBox.x + suitableBox.width - d.width :
                                                  suitableBox.x, suitableBox.y < myRelativeOnScreen.y
                                                                 ? suitableBox.y + suitableBox.height - d.height
                                                                 : suitableBox.y,
                                                  d.width, d.height));

        popup.setSize(crop.getSize());
        if (popup.isVisible()) {
          popup.setLocation(crop.getLocation());
        }
        else {
          popup.show(new RelativePoint(myRelativeTo, new Point(crop.getLocation().x - myRelativeOnScreen.x,
                                                               crop.getLocation().y - myRelativeOnScreen.y)));
        }
      }
    }

    private static Rectangle crop(final Rectangle source, final Rectangle toCrop) {
      final Rectangle result = new Rectangle(toCrop);
      if (toCrop.x < source.x) {
        result.width -= source.x - toCrop.x;
        result.x = source.x;
      }

      if (toCrop.y < source.y) {
        result.height -= source.y - toCrop.y;
        result.y = source.y;
      }

      if (result.x + result.width > source.x + source.width) {
        result.width = source.x + source.width - result.x;
      }

      if (result.y + result.height > source.y + source.height) {
        result.height = source.y + source.height - result.y;
      }

      return result;
    }

    public static Dimension getPopupSize(final JBPopup popup) {
      Dimension size = null;
      if (popup instanceof AbstractPopup) {
        final String dimensionKey = ((AbstractPopup)popup).getDimensionServiceKey();
        if (dimensionKey != null) {
          size = DimensionService.getInstance().getSize(dimensionKey);
        }
      }

      if (size == null) {
        size = popup.getContent().getPreferredSize();
      }

      return size;
    }
  }
}
