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
package com.intellij.ui.components.labels;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.UI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.util.HashSet;
import java.util.Set;

/**
 * @author kir
 */
public class LinkLabel extends JLabel {
  protected boolean myUnderline;

  private LinkListener myLinkListener;
  private Object myLinkData;

  private static final Set ourVisitedLinks = new HashSet();

  private boolean myIsLinkActive;

  private String myVisitedLinksKey;
  private int myIconWidth;
  private Icon myHoveringIcon;
  private Icon myInactiveIcon;

  private boolean myClickIsBeingProcessed;
  private boolean myPaintDefaultIcon;
  protected static final int DEFAULT_ICON_GAP = 2;

  public LinkLabel() {
    this("", AllIcons.Ide.Link);
  }

  public LinkLabel(String text, @Nullable Icon icon) {
    this(text, icon, null, null, null);
  }

  public LinkLabel(String text, @Nullable Icon icon, @Nullable LinkListener aListener) {
    this(text, icon, aListener, null, null);
  }

  public LinkLabel(String text, @Nullable Icon icon, @Nullable LinkListener aListener, @Nullable Object aLinkData) {
    this(text, icon, aListener, aLinkData, null);
  }

  public LinkLabel(String text,
                   @Nullable Icon icon,
                   @Nullable LinkListener aListener,
                   @Nullable Object aLinkData,
                   @Nullable String aVisitedLinksKey) {
    super(text, icon, SwingConstants.LEFT);
    setOpaque(false);

    setListener(aListener, aLinkData);

    myIconWidth = getIcon() == null ? 0 : getIcon().getIconWidth() + getIconTextGap();
    myInactiveIcon = getIcon();

    MyMouseHandler mouseHandler = new MyMouseHandler();
    addMouseListener(mouseHandler);
    addMouseMotionListener(mouseHandler);

    myVisitedLinksKey = aVisitedLinksKey;
  }

  public void setHoveringIcon(Icon iconForHovering) {
    myHoveringIcon = iconForHovering;
  }

  public void setListener(LinkListener listener, @Nullable Object linkData) {
    myLinkListener = listener;
    myLinkData = linkData;
  }

  public void doClick() {
    if (myClickIsBeingProcessed) return;

    try {
      myClickIsBeingProcessed = true;
      if (myLinkListener != null) myLinkListener.linkSelected(this, myLinkData);
      ourVisitedLinks.add(myVisitedLinksKey);
      repaint();
    }
    finally {
      myClickIsBeingProcessed = false;
    }
  }

  public boolean isVisited() {
    return myVisitedLinksKey != null && ourVisitedLinks.contains(myVisitedLinksKey);
  }

  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    final Border border = getBorder();
    int shiftX = 0;
    int shiftY = 0;

    if (border != null) {
      shiftX = border.getBorderInsets(this).left;
      shiftY = border.getBorderInsets(this).top;
    }

    setForeground(getTextColor());

    super.paintComponent(g);


    if (getText() != null) {
      g.setColor(getTextColor());
      int x = myIconWidth;
      int y = getTextBaseLine();

      if (myUnderline) {
        int k = 1;
        if (getFont().getSize() > 11) {
          k += (getFont().getSize() - 11);
        }

        y += k;

        int lineY = y + shiftY;
        if (lineY >= getSize().height) {
          lineY = getSize().height - 1;
        }

        if (getHorizontalAlignment() == LEFT) {
          UIUtil.drawLine(g, x + shiftX, lineY, x + getFontMetrics(getFont()).stringWidth(getText()) + shiftX, lineY);
        } else {
          UIUtil.drawLine(g, getWidth() - 1 - getFontMetrics(getFont()).stringWidth(getText()) + shiftX, lineY,
                          getWidth() - 1 + shiftX, lineY);
        }
      }

      if (myPaintDefaultIcon) {
        int endX = myIconWidth + getFontMetrics(getFont()).stringWidth(getText());
        int endY = getHeight() / 2 - AllIcons.Ide.Link.getIconHeight() / 2 + 1;

        AllIcons.Ide.Link.paintIcon(this, g, endX + shiftX + DEFAULT_ICON_GAP, endY);
      }
    }
  }

  protected Color getTextColor() {
    return myIsLinkActive ? getActive() : isVisited() ? getVisited() : getNormal();
  }

  public Dimension getPreferredSize() {
    final Dimension size = super.getPreferredSize();
    size.width += myPaintDefaultIcon ? AllIcons.Ide.Link.getIconWidth() + DEFAULT_ICON_GAP : 0;
    return size;
  }


  public void removeNotify() {
    super.removeNotify();
    if (ScreenUtil.isStandardAddRemoveNotify(this))
      disableUnderline();
  }

  private void setActive(boolean isActive) {
    myIsLinkActive = isActive;
    onSetActive(myIsLinkActive);
    repaint();
  }

  protected void onSetActive(boolean active) {

  }

  private int getTextBaseLine() {
    FontMetrics fm = getFontMetrics(getFont());
    return getHeight() / 2 + (fm.getHeight() / 2 - fm.getDescent());
  }

  private boolean isInClickableArea(Point pt) {
    if (getIcon() != null) {
      if (pt.getX() < getIcon().getIconWidth() && pt.getY() < getIcon().getIconHeight()) {
        return true;
      }
    }
    if (getText() != null) {
      FontMetrics fm = getFontMetrics(getFont());
      int height = fm.getHeight() + 1;
      int y = getHeight() / 2 - fm.getHeight() / 2;
      int width = fm.stringWidth(getText());
      if (myPaintDefaultIcon) {
        width += AllIcons.Ide.Link.getIconWidth() + DEFAULT_ICON_GAP;
      }

      if (getHorizontalAlignment() == LEFT) {
        return (new Rectangle(myIconWidth, y, width, height).contains(pt));
      }
      else {
        return (new Rectangle(getWidth() - width - 1, y, getWidth() - 1, height).contains(pt));
      }
    }

    return false;
  }

  private void enableUnderline() {
    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    myUnderline = true;
    if (myHoveringIcon != null) {
      setIcon(myHoveringIcon);
    }
    setStatusBarText(getStatusBarText());
    repaint();
  }

  protected String getStatusBarText() {
    return getToolTipText();
  }

  private void disableUnderline() {
    setCursor(Cursor.getDefaultCursor());
    myUnderline = false;
    setIcon(myInactiveIcon);
    setStatusBarText(null);
    setActive(false);
  }

  private static void setStatusBarText(String statusBarText) {
    if (ApplicationManager.getApplication() == null) return; // makes this component work in UIDesigner preview.
    final Project[] projects = ProjectManager.getInstance().getOpenProjects();
    for (Project project : projects) {
      StatusBar.Info.set(statusBarText, project);
    }
  }

  public static void clearVisitedHistory() {
    ourVisitedLinks.clear();
  }

  protected Color getVisited() {
    return UI.getColor("link.visited.foreground");
  }

  protected Color getActive() {
    return UI.getColor("link.pressed.foreground");
  }

  protected Color getNormal() {
    return UI.getColor("link.foreground");
  }

  public void entered(MouseEvent e) {
    enableUnderline();
  }

  public void exited(MouseEvent e) {
    disableUnderline();
  }

  public void pressed(MouseEvent e) {
    doClick(e);
  }

  private class MyMouseHandler extends MouseAdapter implements MouseMotionListener {
    public void mousePressed(MouseEvent e) {
      if (isInClickableArea(e.getPoint())) {
        setActive(true);
      }
    }

    public void mouseReleased(MouseEvent e) {
      if (myIsLinkActive && isInClickableArea(e.getPoint())) {
        doClick(e);
      }
      setActive(false);
    }

    public void mouseMoved(MouseEvent e) {
      if (isInClickableArea(e.getPoint())) {
        enableUnderline();
      }
      else {
        disableUnderline();
      }
    }

    public void mouseExited(MouseEvent e) {
      disableUnderline();
    }

    public void mouseDragged(MouseEvent e) {
    }
  }

  public void doClick(InputEvent e) {
    doClick();
  }

  public void setDefaultIconPainted(boolean paintDefaultIcon) {
    myPaintDefaultIcon = paintDefaultIcon;
  }

}
