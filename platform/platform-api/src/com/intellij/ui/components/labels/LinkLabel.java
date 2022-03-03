// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components.labels;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.paint.RectanglePainter;
import com.intellij.util.ui.JBRectangle;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.ScreenReader;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.AccessibleAction;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.HashSet;
import java.util.Set;

/**
 * A {@link JLabel}-based link that does not support mnemonics.
 * Prefer using {@link com.intellij.ui.components.ActionLink} instead.
 * @author kir
 * @see <a href="https://jetbrains.github.io/ui/controls/link/">IJ Platform UI Guidelines | Link</a>
 */
public class LinkLabel<T> extends JLabel {
  protected boolean myUnderline;

  private LinkListener<T> myLinkListener;
  private T myLinkData;

  private static final Set<String> ourVisitedLinks = new HashSet<>();

  private boolean myIsLinkActive;

  private final String myVisitedLinksKey;
  private Icon myHoveringIcon;
  private Icon myInactiveIcon;

  private boolean myClickIsBeingProcessed;
  protected boolean myPaintUnderline = true;

  /**
   * Creates a {@link JLabel}-based link that does not support mnemonics.
   * Prefer using {@link com.intellij.ui.components.ActionLink} instead.
   * Note that this constructor sets inappropriate icon.
   * @see <a href="https://jetbrains.github.io/ui/controls/link/">UI Guidelines</a>
   */
  public LinkLabel() {
    this("", AllIcons.Ide.Link);
  }

  /**
   * Creates a {@link JLabel}-based link that does not support mnemonics.
   * Prefer using {@link com.intellij.ui.components.ActionLink} instead.
   * @see <a href="https://jetbrains.github.io/ui/controls/link/">UI Guidelines</a>
   */
  public LinkLabel(@NlsContexts.LinkLabel String text, @Nullable Icon icon) {
    this(text, icon, null, null, null);
  }

  /**
   * Creates a {@link JLabel}-based link that does not support mnemonics.
   * Prefer using {@link com.intellij.ui.components.ActionLink} instead.
   * @see <a href="https://jetbrains.github.io/ui/controls/link/">UI Guidelines</a>
   */
  public LinkLabel(@NlsContexts.LinkLabel String text, @Nullable Icon icon, @Nullable LinkListener<T> aListener) {
    this(text, icon, aListener, null, null);
  }

  /**
   * @see <a href="https://jetbrains.github.io/ui/controls/link/">UI Guidelines</a>
   * @deprecated use {@link com.intellij.ui.components.ActionLink} instead
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.3")
  @Deprecated
  @NotNull
  public static LinkLabel<?> create(@Nullable @NlsContexts.LinkLabel String text, @Nullable Runnable action) {
    return new LinkLabel<>(text, null, action == null ? null : (__, ___) -> action.run(), null, null);
  }

  /**
   * Creates a {@link JLabel}-based link that does not support mnemonics.
   * Prefer using {@link com.intellij.ui.components.ActionLink} instead.
   * @see <a href="https://jetbrains.github.io/ui/controls/link/">UI Guidelines</a>
   */
  public LinkLabel(@NlsContexts.LinkLabel String text, @Nullable Icon icon, @Nullable LinkListener<T> aListener, @Nullable T aLinkData) {
    this(text, icon, aListener, aLinkData, null);
  }

  /**
   * @see <a href="https://jetbrains.github.io/ui/controls/link/">UI Guidelines</a>
   * @deprecated use {@link com.intellij.ui.components.ActionLink} instead
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
  @Deprecated
  public LinkLabel(@NlsContexts.LinkLabel String text,
                   @Nullable Icon icon,
                   @Nullable LinkListener<T> aListener,
                   @Nullable T aLinkData,
                   @Nullable String aVisitedLinksKey) {
    super(text, icon, SwingConstants.LEFT);
    setOpaque(false);
    // Note: Ideally, we should be focusable by default in all cases, however,
    // to preserve backward compatibility with existing behavior, we make
    // ourselves focusable only when a screen reader is active.
    setFocusable(ScreenReader.isActive());

    setListener(aListener, aLinkData);
    myInactiveIcon = getIcon();

    MyMouseHandler mouseHandler = new MyMouseHandler();
    addMouseListener(mouseHandler);
    addMouseMotionListener(mouseHandler);
    addKeyListener(new KeyAdapter() {
      @Override
      public void keyReleased(KeyEvent e) {
        super.keyReleased(e);
        if (e.getModifiers() == 0 && e.getKeyCode() == KeyEvent.VK_SPACE) {
          e.consume();
          doClick();
        }
      }
    });
    addFocusListener(new FocusListener() {
      @Override
      public void focusGained(FocusEvent e) {
        myUnderline = true;
        repaint();
      }

      @Override
      public void focusLost(FocusEvent e) {
        myUnderline = false;
        repaint();
      }
    });

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
      if (myLinkListener != null) {
        myLinkListener.linkSelected(this, myLinkData);
      }
      if (myVisitedLinksKey != null) {
        ourVisitedLinks.add(myVisitedLinksKey);
      }
      repaint();
    }
    finally {
      myClickIsBeingProcessed = false;
    }
  }

  public boolean isVisited() {
    return myVisitedLinksKey != null && ourVisitedLinks.contains(myVisitedLinksKey);
  }

  @Override
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

      if (g instanceof Graphics2D && isFocusOwner()) {
        g.setColor(JBUI.CurrentTheme.Link.FOCUSED_BORDER_COLOR);
        Rectangle bounds = getTextBounds();
        int round = Registry.intValue("ide.link.button.focus.round.arc", 4);
        RectanglePainter.DRAW.paint((Graphics2D)g, bounds.x, bounds.y, bounds.width, bounds.height, JBUI.scale(round));
      }
    }
  }

  @NotNull
  protected Rectangle getTextBounds() {
    if (textR.isEmpty()) {
      updateLayoutRectangles();
    }
    return textR;
  }

  protected Color getTextColor() {
    return myIsLinkActive ? getActive() :
           myUnderline ? getHover() :
           isVisited() ? getVisited() : getNormal();
  }

  public void setPaintUnderline(boolean paintUnderline) {
    myPaintUnderline = paintUnderline;
  }

  @Override
  public void removeNotify() {
    super.removeNotify();
    if (ScreenUtil.isStandardAddRemoveNotify(this)) {
      disableUnderline();
    }
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

  protected boolean isInClickableArea(Point pt) {
    updateLayoutRectangles();
    if (getIcon() != null) {
      iconR.width += getIconTextGap(); //todo[kb] icon at right?
      if (iconR.contains(pt)) {
        return true;
      }
    }
    return textR.contains(pt);
  }

  private void updateLayoutRectangles() {
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
  }

  //for GUI tests
  public Point getTextRectangleCenter() {
    isInClickableArea(new Point(0, 0)); //to update textR before clicking
    return new Point(textR.x + textR.width / 2, textR.y + textR.height / 2);
  }

  private void enableUnderline() {
    UIUtil.setCursor(this, Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    myUnderline = true;
    if (myHoveringIcon != null) {
      super.setIcon(myHoveringIcon);
    }
    setStatusBarText(getStatusBarText());
    repaint();
  }

  protected @NlsContexts.StatusBarText String getStatusBarText() {
    return getToolTipText();
  }

  private void disableUnderline() {
    UIUtil.setCursor(this, Cursor.getDefaultCursor());
    myUnderline = false;
    super.setIcon(myInactiveIcon);
    setStatusBarText(null);
    setActive(false);
  }

  private static void setStatusBarText(@NlsContexts.StatusBarText String statusBarText) {
    if (ApplicationManager.getApplication() == null) return; // makes this component work in UIDesigner preview.
    final Project[] projects = ProjectManager.getInstance().getOpenProjects();
    for (Project project : projects) {
      StatusBar.Info.set(statusBarText, project);
    }
  }

  protected Color getVisited() {
    return JBUI.CurrentTheme.Link.Foreground.VISITED;
  }

  protected Color getActive() {
    return JBUI.CurrentTheme.Link.Foreground.PRESSED;
  }

  protected Color getNormal() {
    return JBUI.CurrentTheme.Link.Foreground.ENABLED;
  }

  protected Color getHover() {
    return JBUI.CurrentTheme.Link.Foreground.HOVERED;
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
    @Override
    public void mousePressed(MouseEvent e) {
      if (isEnabled() && isInClickableArea(e.getPoint())) {
        setActive(true);
        e.consume();
      }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
      if (isEnabled() && myIsLinkActive && isInClickableArea(e.getPoint())) {
        doClick(e);
        e.consume();
      }
      setActive(false);
    }

    @Override
    public void mouseMoved(MouseEvent e) {
      if (isEnabled() && isInClickableArea(e.getPoint())) {
        enableUnderline();
      }
      else {
        disableUnderline();
      }
    }

    @Override
    public void mouseExited(MouseEvent e) {
      disableUnderline();
    }

    @Override
    public void mouseDragged(MouseEvent e) {
    }
  }

  public void doClick(InputEvent e) {
    doClick();
  }

  @Override
  public AccessibleContext getAccessibleContext() {
    if (accessibleContext == null) {
      accessibleContext = new AccessibleLinkLabel();
    }
    return accessibleContext;
  }

  protected class AccessibleLinkLabel extends AccessibleJLabel implements AccessibleAction {
    @Override
    public AccessibleRole getAccessibleRole() {
      return AccessibleRole.HYPERLINK;
    }

    @Override
    public int getAccessibleActionCount() {
      return 1;
    }

    @Override
    public String getAccessibleActionDescription(int i) {
      if (i == 0) {
        return UIManager.getString("AbstractButton.clickText");
      }
      else {
        return null;
      }
    }

    @Override
    public boolean doAccessibleAction(int i) {
      if (i == 0) {
        doClick();
        return true;
      }
      else {
        return false;
      }
    }
  }
}
