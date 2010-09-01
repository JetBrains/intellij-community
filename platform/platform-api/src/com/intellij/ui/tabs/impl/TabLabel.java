/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.util.Pass;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleColoredText;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.tabs.JBTabsPosition;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.UiDecorator;
import com.intellij.util.ui.Centerizer;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

public class TabLabel extends JPanel {
  private final SimpleColoredComponent myLabel = new SimpleColoredComponent();
  private final LayeredIcon myIcon;
  private Icon myOverlayedIcon;

  private final TabInfo myInfo;
  private ActionPanel myActionPanel;
  private boolean myCentered;

  private final Wrapper myLabelPlaceholder = new Wrapper();
  private final JBTabsImpl myTabs;

  private BufferedImage myImage;

  private BufferedImage myInactiveStateImage;
  private Rectangle myLastPaintedInactiveImageBounds;
  private boolean myStretchedByWidth;

  public TabLabel(JBTabsImpl tabs, final TabInfo info) {
    myTabs = tabs;
    myInfo = info;
    myLabel.setOpaque(false);
    myLabel.setBorder(null);
    myLabel.setIconTextGap(new JLabel().getIconTextGap());
    myLabel.setIconOpaque(false);
    myLabel.setIpad(new Insets(0, 0, 0, 0));
    setOpaque(false);
    setLayout(new BorderLayout());

    myLabelPlaceholder.setOpaque(false);
    add(myLabelPlaceholder, BorderLayout.CENTER);

    setAligmentToCenter(true);

    myIcon = new LayeredIcon(2);
    myLabel.setIcon(myIcon);

    addMouseListener(new MouseAdapter() {
      public void mousePressed(final MouseEvent e) {
        if (myTabs.isSelectionClick(e, false) && myInfo.isEnabled()) {
          myTabs.select(info, true);
        }
        else {
          handlePopup(e);
        }
      }

      public void mouseClicked(final MouseEvent e) {
        handlePopup(e);
      }

      public void mouseReleased(final MouseEvent e) {
        handlePopup(e);
      }
    });
  }

  public void setAligmentToCenter(boolean toCenter) {
    if (myCentered == toCenter && myLabel.getParent() != null) return;

    myLabelPlaceholder.removeAll();

    if (toCenter) {
      final Centerizer center = new Centerizer(myLabel);
      myLabelPlaceholder.setContent(center);
    } else {
      myLabelPlaceholder.setContent(myLabel);
    }

    myCentered = toCenter;
  }

  public void paint(final Graphics g) {
    if (myTabs.getSelectedInfo() != myInfo) {
      myImage = null;
      doPaint(g);
    } else if (!SystemInfo.isMac) {
      myImage = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
      final Graphics2D lg = myImage.createGraphics();
      doPaint(lg);
      lg.dispose();
    }
  }

  public void paintImage(Graphics g) {
    final Rectangle b = getBounds();
    if (myImage != null) {
      g.drawImage(myImage, b.x, b.y, getWidth(), getHeight(), null);
    } else {
      final Graphics lG = g.create(b.x, b.y, b.width, b.height);
      try {
        lG.setColor(Color.red);
        doPaint(lG);
      }
      finally {
        lG.dispose();
      }
    }
  }

  private void doPaint(Graphics g) {
    final JBTabsPosition pos = myTabs.getTabsPosition();

    int dX = 0;
    int dXs = 0;
    int dY = 0;
    int dYs = 0;
    int selected = getSelectedOffset();
    int plain = getNonSelectedOffset();

    switch (pos) {
      case bottom:
        dY = -plain;
        dYs = -selected;
        break;
      case left:
        dX = plain;
        dXs = selected;
        break;
      case right:
        dX = -plain;
        dXs = -selected;
        break;
      case top:
        dY = plain;
        dYs = selected;
        break;
    }

    if (myTabs.getSelectedInfo() != myInfo) {
      g.translate(dX, dY);
    } else {
      g.translate(dXs, dYs);
    }

    super.paint(g);

    if (myTabs.getSelectedInfo() != myInfo) {
      g.translate(-dX, -dY);
    } else {
      g.translate(-dXs, -dYs);
    }
  }

  private int getNonSelectedOffset() {
    return 2;
  }

  private int getSelectedOffset() {
    return 1;
  }

  @Override
  public Dimension getPreferredSize() {
    final Dimension size = super.getPreferredSize();

    final JBTabsPosition pos = myTabs.getTabsPosition();
    switch (pos) {
      case top: case bottom: size.height += getSelectedOffset(); break;
      case left: case right: size.width += getSelectedOffset(); break;
    }

    return size;
  }

  private void handlePopup(final MouseEvent e) {
    if (e.getClickCount() != 1 || !e.isPopupTrigger()) return;

    if (e.getX() < 0 || e.getX() >= e.getComponent().getWidth() || e.getY() < 0 || e.getY() >= e.getComponent().getHeight()) return;

    String place = myTabs.getPopupPlace();
    place = place != null ? place : ActionPlaces.UNKNOWN;
    myTabs.myPopupInfo = myInfo;

    final DefaultActionGroup toShow = new DefaultActionGroup();
    if (myTabs.getPopupGroup() != null) {
      toShow.addAll(myTabs.getPopupGroup());
      toShow.addSeparator();
    }

    JBTabsImpl tabs = JBTabsImpl.NAVIGATION_ACTIONS_KEY.getData(DataManager.getInstance().getDataContext(e.getComponent(), e.getX(), e.getY()));
    if (tabs == myTabs && myTabs.myAddNavigationGroup) {
      toShow.addAll(myTabs.myNavigationActions);
    }

    if (toShow.getChildrenCount() == 0) return;

    myTabs.myActivePopup = myTabs.myActionManager.createActionPopupMenu(place, toShow).getComponent();
    myTabs.myActivePopup.addPopupMenuListener(myTabs.myPopupListener);

    myTabs.myActivePopup.addPopupMenuListener(myTabs);
    myTabs.myActivePopup.show(e.getComponent(), e.getX(), e.getY());
    myTabs.onPopup(myTabs.myPopupInfo);
  }


  public void setText(final SimpleColoredText text) {
    myLabel.change(new Runnable() {
      public void run() {
        myLabel.clear();
        myLabel.setIcon(myIcon);

         if (text != null) {
          text.appendToComponent(myLabel);
        }
      }
    }, false);


    invalidateIfNeeded();
  }


  private void invalidateIfNeeded() {
    if (myLabel.getRootPane() == null) return;

    if (myLabel.getSize() != null && myLabel.getSize().equals(myLabel.getPreferredSize())) return;

    setInactiveStateImage(null);

    myLabel.invalidate();

    if (myActionPanel != null) {
      myActionPanel.invalidate();
    }

    myTabs.revalidateAndRepaint(false);
  }

  public void setIcon(final Icon icon) {
    getLayeredIcon().setIcon(icon, 0);
    invalidateIfNeeded();
  }

  private LayeredIcon getLayeredIcon() {
    return myIcon;
  }

  public void setAttraction(boolean enabled) {
    getLayeredIcon().setLayerEnabled(1, enabled);
  }

  public boolean isAttractionEnabled() {
    return getLayeredIcon().isLayerEnabled(1);
  }

  public TabInfo getInfo() {
    return myInfo;
  }

  public void apply(UiDecorator.UiDecoration decoration) {
    if (decoration.getLabelFont() != null) {
      setFont(decoration.getLabelFont());
    }

    Insets insets = decoration.getLabelInsets();
    if (insets != null) {
      Insets current = JBTabsImpl.ourDefaultDecorator.getDecoration().getLabelInsets();
      if (current != null) {
        setBorder(
          new EmptyBorder(getValue(current.top, insets.top), getValue(current.left, insets.left), getValue(current.bottom, insets.bottom),
                          getValue(current.right, insets.right)));
      }
    }
  }

  private static int getValue(int curentValue, int newValue) {
    return newValue != -1 ? newValue : curentValue;
  }

  public void setTabActions(ActionGroup group) {
    removeOldActionPanel();

    if (group == null) return;

    myActionPanel = new ActionPanel(myTabs, myInfo, new Pass<MouseEvent>() {
      public void pass(final MouseEvent event) {
        final MouseEvent me = SwingUtilities.convertMouseEvent(event.getComponent(), event, TabLabel.this);
        processMouseEvent(me);
      }
    });

    toggleShowActions(false);

    add(myActionPanel, BorderLayout.EAST);

    myTabs.revalidateAndRepaint(false);
  }

  @Override
  protected void processMouseEvent(final MouseEvent e) {
    super.processMouseEvent(e);
  }

  private void removeOldActionPanel() {
    if (myActionPanel != null) {
      myActionPanel.getParent().remove(myActionPanel);
      myActionPanel = null;
    }
  }

  public boolean updateTabActions() {
    return myActionPanel != null && myActionPanel.update();

  }

  private void setAttractionIcon(Icon icon) {
    if (myIcon.getIcon(0) == null) {
      getLayeredIcon().setIcon(null, 1);
      myOverlayedIcon = icon;
    } else {
      getLayeredIcon().setIcon(icon, 1);
      myOverlayedIcon = null;
    }
  }

  public boolean repaintAttraction() {
    if (!myTabs.myAttractions.contains(myInfo)) {
      if (getLayeredIcon().isLayerEnabled(1)) {
        getLayeredIcon().setLayerEnabled(1, false);
        setAttractionIcon(null);
        invalidateIfNeeded();
        return true;
      }
      return false;
    }

    boolean needsUpdate = false;

    if (getLayeredIcon().getIcon(1) != myInfo.getAlertIcon()) {
      setAttractionIcon(myInfo.getAlertIcon());
      needsUpdate = true;
    }

    int maxInitialBlinkCount = 5;
    int maxRefireBlinkCount = maxInitialBlinkCount + 2;
    if (myInfo.getBlinkCount() < maxInitialBlinkCount && myInfo.isAlertRequested()) {
      getLayeredIcon().setLayerEnabled(1, !getLayeredIcon().isLayerEnabled(1));
      if (myInfo.getBlinkCount() == 0) {
        needsUpdate = true;
      }
      myInfo.setBlinkCount(myInfo.getBlinkCount() + 1);

      if (myInfo.getBlinkCount() == maxInitialBlinkCount) {
        myInfo.resetAlertRequest();
      }

      repaint();
    }
    else {
      if (myInfo.getBlinkCount() < maxRefireBlinkCount && myInfo.isAlertRequested()) {
        getLayeredIcon().setLayerEnabled(1, !getLayeredIcon().isLayerEnabled(1));
        myInfo.setBlinkCount(myInfo.getBlinkCount() + 1);

        if (myInfo.getBlinkCount() == maxRefireBlinkCount) {
          myInfo.setBlinkCount(maxInitialBlinkCount);
          myInfo.resetAlertRequest();
        }

        repaint();
      }
      else {
        needsUpdate = !getLayeredIcon().isLayerEnabled(1);
        getLayeredIcon().setLayerEnabled(1, true);
      }
    }

    invalidateIfNeeded();

    return needsUpdate;
  }

  protected void paintChildren(final Graphics g) {
    super.paintChildren(g);

    if (myOverlayedIcon == null || myLabel.getParent() == null) return;

    final Rectangle textBounds = SwingUtilities.convertRectangle(myLabel.getParent(), myLabel.getBounds(), this);
    if (getLayeredIcon().isLayerEnabled(1)) {

      final int top = (getSize().height - myOverlayedIcon.getIconHeight()) / 2;

      myOverlayedIcon.paintIcon(this, g, textBounds.x - myOverlayedIcon.getIconWidth() / 2, top);
    }

  }

  public void setTabActionsAutoHide(final boolean autoHide) {
    if (myActionPanel == null || myActionPanel.isAutoHide() == autoHide) {
      return;
    }

    myActionPanel.setAutoHide(autoHide);
  }

  public void toggleShowActions(boolean show) {
    if (myActionPanel != null) {
      myActionPanel.toggleShowActtions(show);
    }
  }

  @Override
  public String toString() {
    return myInfo.getText();
  }

  public void setTabEnabled(boolean enabled) {
    myLabel.setEnabled(enabled);
  }


  public BufferedImage getInactiveStateImage(Rectangle effectiveBounds) {
    BufferedImage img = null;
    if (myLastPaintedInactiveImageBounds != null && myLastPaintedInactiveImageBounds.getSize().equals(effectiveBounds.getSize())) {
      img = myInactiveStateImage;
    } else {
      setInactiveStateImage(null);
    }
    myLastPaintedInactiveImageBounds = effectiveBounds;
    return img;
  }

  public void setInactiveStateImage(BufferedImage img) {
    if (myInactiveStateImage != null && img != myInactiveStateImage) {
      myInactiveStateImage.flush();
    }
    myInactiveStateImage = img;
  }
}
