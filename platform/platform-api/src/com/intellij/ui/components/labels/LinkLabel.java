/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.util.ui.JBRectangle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
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
public class LinkLabel<T> extends JLabel {
  protected boolean myUnderline;

  private LinkListener<T> myLinkListener;
  private T myLinkData;

  private static final Set<String> ourVisitedLinks = new HashSet<String>();

  private boolean myIsLinkActive;

  private String myVisitedLinksKey;
  private Icon myHoveringIcon;
  private Icon myInactiveIcon;

  private boolean myClickIsBeingProcessed;
  protected boolean myPaintUnderline = true;

  public LinkLabel() {
    this("", AllIcons.Ide.Link);
  }

  public LinkLabel(String text, @Nullable Icon icon) {
    this(text, icon, null, null, null);
  }

  public LinkLabel(String text, @Nullable Icon icon, @Nullable LinkListener<T> aListener) {
    this(text, icon, aListener, null, null);
  }

  public LinkLabel(String text, @Nullable Icon icon, @Nullable LinkListener<T> aListener, @Nullable T aLinkData) {
    this(text, icon, aListener, aLinkData, null);
  }

  public LinkLabel(String text,
                   @Nullable Icon icon,
                   @Nullable LinkListener<T> aListener,
                   @Nullable T aLinkData,
                   @Nullable String aVisitedLinksKey) {
    super(text, icon, SwingConstants.LEFT);
    setOpaque(false);

    setListener(aListener, aLinkData);
    myInactiveIcon = getIcon();

    MyMouseHandler mouseHandler = new MyMouseHandler();
    addMouseListener(mouseHandler);
    addMouseMotionListener(mouseHandler);

    myVisitedLinksKey = aVisitedLinksKey;
  }

  @Override
  public void setIcon(Icon icon) {
    super.setIcon(icon);
    myInactiveIcon = icon;
  }

  public void setHoveringIcon(Icon iconForHovering) {
    myHoveringIcon = iconForHovering;
  }

  public void setListener(LinkListener<T> listener, @Nullable T linkData) {
    myLinkListener = listener;
    myLinkData = linkData;
  }

  public T getLinkData() {
    return myLinkData;
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
    setForeground(getTextColor());
    super.paintComponent(g);

    if (getText() != null) {
      g.setColor(getTextColor());

      if (myUnderline && myPaintUnderline) {
        Rectangle bounds = getTextBounds();
        int lineY = getUI().getBaseline(this, getWidth(), getHeight()) + 1;
        g.drawLine(bounds.x, lineY, bounds.x + bounds.width, lineY);
      }
    }
  }

  @NotNull
  protected Rectangle getTextBounds() {
    final Dimension size = getPreferredSize();
    Icon icon = getIcon();
    final Point point = new Point(0, 0);
    final Insets insets = getInsets();
    if (icon != null) {
      point.x += getIconTextGap();
      point.x += icon.getIconWidth();
    }
    point.x += insets.left;
    point.y += insets.top;
    size.width -= point.x;
    size.width -= insets.right;
    size.height -= insets.bottom;

    return new Rectangle(point, size);
  }

  protected Color getTextColor() {
    return myIsLinkActive ? getActive() : isVisited() ? getVisited() : getNormal();
  }

  public void setPaintUnderline(boolean paintUnderline) {
    myPaintUnderline = paintUnderline;
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

  private final JBRectangle iconR = new JBRectangle();
  private final JBRectangle textR = new JBRectangle();
  private final JBRectangle viewR = new JBRectangle();

  private boolean isInClickableArea(Point pt) {
    iconR.clear();
    textR.clear();
    final Insets insets = getInsets(null);
    viewR.x = insets.left;
    viewR.y = insets.top;
    viewR.width = getWidth() - (insets.left + insets.right);
    viewR.height = getHeight() - (insets.top + insets.bottom);
    SwingUtilities.layoutCompoundLabel(this,
                                       getFontMetrics(getFont()),
                                       getText(),
                                       isEnabled() ? getIcon() : getDisabledIcon(),
                                       getVerticalAlignment(),
                                       getHorizontalAlignment(),
                                       getVerticalTextPosition(),
                                       getHorizontalTextPosition(),
                                       viewR,
                                       iconR,
                                       textR,
                                       getIconTextGap());
    if (getIcon() != null) {
      iconR.width += getIconTextGap(); //todo[kb] icon at right?
      if (iconR.contains(pt)) {
        return true;
      }
    }
    return textR.contains(pt);
  }

  private void enableUnderline() {
    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    myUnderline = true;
    if (myHoveringIcon != null) {
      super.setIcon(myHoveringIcon);
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
    super.setIcon(myInactiveIcon);
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
}
