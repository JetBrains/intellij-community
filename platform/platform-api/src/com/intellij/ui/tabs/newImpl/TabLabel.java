// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.newImpl;

import com.intellij.ide.DataManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.ui.*;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.tabs.JBTabPainter;
import com.intellij.ui.tabs.JBTabsEx;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.UiDecorator;
import com.intellij.ui.tabs.newImpl.themes.TabTheme;
import com.intellij.util.ui.Centerizer;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.ScreenReader;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;

public class TabLabel extends JPanel implements Accessible, Disposable {
  // If this System property is set to true 'close' button would be shown on the left of text (it's on the right by default)
  protected final SimpleColoredComponent myLabel;

  private final LayeredIcon myIcon;
  private Icon myOverlayedIcon;

  private final TabInfo myInfo;
  protected ActionPanel myActionPanel;
  private boolean myCentered;

  private final Wrapper myLabelPlaceholder = new Wrapper(false);
  protected final JBTabsImpl myTabs;

  public TabLabel(JBTabsImpl tabs, final TabInfo info) {
    super(false);

    myTabs = tabs;
    myInfo = info;

    myLabel = createLabel(tabs);

    // Allow focus so that user can TAB into the selected TabLabel and then
    // navigate through the other tabs using the LEFT/RIGHT keys.
    setFocusable(ScreenReader.isActive());
    setOpaque(false);
    setLayout(new BorderLayout());

    myLabelPlaceholder.setOpaque(false);
    add(myLabelPlaceholder, BorderLayout.CENTER);

    setAlignmentToCenter(true);

    myIcon = new LayeredIcon(2);

    addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(final MouseEvent e) {
        if (UIUtil.isCloseClick(e, MouseEvent.MOUSE_PRESSED)) return;
        if (JBTabsImpl.isSelectionClick(e, false) && myInfo.isEnabled()) {
          final TabInfo selectedInfo = myTabs.getSelectedInfo();
          if (selectedInfo != myInfo) {
            myInfo.setPreviousSelection(selectedInfo);
          }
          Component c = SwingUtilities.getDeepestComponentAt(e.getComponent(), e.getX(), e.getY());
          if (c instanceof InplaceButton) return;
          myTabs.select(info, true);
        }
        else {
          handlePopup(e);
        }
      }

      @Override
      public void mouseClicked(final MouseEvent e) {
        handlePopup(e);
      }

      @Override
      public void mouseReleased(final MouseEvent e) {
        myInfo.setPreviousSelection(null);
        handlePopup(e);
      }

      @Override
      public void mouseEntered(MouseEvent e) {
        setHovered(true);
      }

      @Override
      public void mouseExited(MouseEvent e) {
        setHovered(false);
      }
    });

    if (isFocusable()) {
      // Navigate to the previous/next tab when LEFT/RIGHT is pressed.
      addKeyListener(new KeyAdapter() {
        @Override
        public void keyPressed(KeyEvent e) {
          if (e.getKeyCode() == KeyEvent.VK_LEFT) {
            int index = myTabs.getIndexOf(myInfo);
            if (index >= 0) {
              e.consume();
              // Select the previous tab, then set the focus its TabLabel.
              TabInfo previous = myTabs.findEnabledBackward(index, true);
              if (previous != null) {
                myTabs.select(previous, false).doWhenDone(() -> myTabs.getSelectedLabel().requestFocusInWindow());
              }
            }
          }
          else if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
            int index = myTabs.getIndexOf(myInfo);
            if (index >= 0) {
              e.consume();
              // Select the previous tab, then set the focus its TabLabel.
              TabInfo next = myTabs.findEnabledForward(index, true);
              if (next != null) {
                // Select the next tab, then set the focus its TabLabel.
                myTabs.select(next, false).doWhenDone(() -> myTabs.getSelectedLabel().requestFocusInWindow());
              }
            }
          }
        }
      });

      // Repaint when we gain/lost focus so that the focus cue is displayed.
      addFocusListener(new FocusListener() {
        @Override
        public void focusGained(FocusEvent e) {
          repaint();
        }

        @Override
        public void focusLost(FocusEvent e) {
          repaint();
        }
      });
    }
  }

  @Override
  public void dispose() {
  }

  private void setHovered(boolean value) {
    if (myTabs.isHoveredTab(this) == value) return;
    if (value) {
      myTabs.setHovered(this);
    }
    else {
      myTabs.unHover(this);
    }
  }

  @Override
  public boolean isFocusable() {
    // We don't want the focus unless we are the selected tab.
    if (myTabs.getSelectedLabel() != this) {
      return false;
    }

    return super.isFocusable();
  }

  private SimpleColoredComponent createLabel(final JBTabsImpl tabs) {
    SimpleColoredComponent label = new SimpleColoredComponent() {
      @Override
      protected boolean shouldDrawDimmed() {
        return myTabs.getSelectedInfo() != myInfo || myTabs.useBoldLabels();
      }

      @Override
      public Font getFont() {
        if (isFontSet() || !myTabs.useSmallLabels()) {
          return super.getFont();
        }
        return UIUtil.getLabelFont(UIUtil.FontSize.SMALL);
      }

      @Override
      protected Color getActiveTextColor(Color attributesColor) {
        JBTabPainter painter = myTabs.getTabPainter();
        TabTheme theme = painter.getTabTheme();
        return myTabs.getSelectedInfo() == myInfo && (UIUtil.getLabelForeground().equals(attributesColor) || attributesColor == null) ?
               myTabs.isActiveTabs(myInfo) ? theme.getUnderlinedTabForeground() : theme.getUnderlinedTabInactiveForeground()
                                                   : super.getActiveTextColor(attributesColor);
      }

    };
    label.setOpaque(false);
    label.setBorder(null);
    label.setIconTextGap(
      tabs.isEditorTabs() ? (!UISettings.getShadowInstance().getHideTabsIfNeeded() ? 4 : 2) + 1 : new JLabel().getIconTextGap());
    label.setIconOpaque(false);
    label.setIpad(JBUI.emptyInsets());

    return label;
  }

  @Override
  public Insets getInsets() {
    Insets insets = super.getInsets();
    if (myTabs.isEditorTabs() && UISettings.getShadowInstance().getShowCloseButton() && hasIcons()) {
      if (UISettings.getShadowInstance().getCloseTabButtonOnTheRight()) {
        insets.right -= JBUIScale.scale(4);
      }
      else {
        insets.left -= JBUIScale.scale(4);
      }
    }
    return insets;
  }

  public void setAlignmentToCenter(boolean toCenter) {
    if (myCentered == toCenter && getLabelComponent().getParent() != null) return;

    setPlaceholderContent(toCenter, getLabelComponent());
  }

  protected void setPlaceholderContent(boolean toCenter, JComponent component) {
    myLabelPlaceholder.removeAll();

    if (toCenter /*&& !Registry.is("ide.new.editor.tabs.selection")*/) {
      final Centerizer center = new Centerizer(component);
      myLabelPlaceholder.setContent(center);
    }
    else {
      myLabelPlaceholder.setContent(component);
    }

    myCentered = toCenter;
  }


  public void paintOffscreen(Graphics g) {
    synchronized (getTreeLock()) {
      validateTree();
    }
    doPaint(g);
  }

  @Override
  public void paint(final Graphics g) {
    if (myTabs.isDropTarget(myInfo)) return;
    doPaint(g);
  }

  private void doPaint(final Graphics g) {
    super.paint(g);
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

    JBTabsImpl tabs =
      (JBTabsImpl)JBTabsEx.NAVIGATION_ACTIONS_KEY.getData(DataManager.getInstance().getDataContext(e.getComponent(), e.getX(), e.getY()));
    if (tabs == myTabs && myTabs.myAddNavigationGroup) {
      toShow.addAll(myTabs.myNavigationActions);
    }

    if (toShow.getChildrenCount() == 0) return;

    myTabs.myActivePopup = myTabs.myActionManager.createActionPopupMenu(place, toShow).getComponent();
    myTabs.myActivePopup.addPopupMenuListener(myTabs.myPopupListener);

    myTabs.myActivePopup.addPopupMenuListener(myTabs);
    myTabs.myActivePopup.show(e.getComponent(), e.getX(), e.getY());
  }


  public void setText(final SimpleColoredText text) {
    myLabel.change(() -> {
      myLabel.clear();
      myLabel.setIcon(hasIcons() ? myIcon : null);

      if (text != null) {
        SimpleColoredText derive = myTabs.useBoldLabels() ? text.derive(SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES, true) : text;
        derive.appendToComponent(myLabel);
      }
    }, false);

    invalidateIfNeeded();
  }


  private void invalidateIfNeeded() {
    if (getLabelComponent().getRootPane() == null) return;

    Dimension d = getLabelComponent().getSize();
    Dimension pref = getLabelComponent().getPreferredSize();
    if (d != null && d.equals(pref)) {
      return;
    }

    getLabelComponent().invalidate();

    if (myActionPanel != null) {
      myActionPanel.invalidate();
    }

    myTabs.revalidateAndRepaint(false);
  }

  public void setIcon(final Icon icon) {
    setIcon(icon, 0);
  }

  private boolean hasIcons() {
    LayeredIcon layeredIcon = getLayeredIcon();
    boolean hasIcons = false;
    Icon[] layers = layeredIcon.getAllLayers();
    for (Icon layer1 : layers) {
      if (layer1 != null) {
        hasIcons = true;
        break;
      }
    }

    return hasIcons;
  }

  private void setIcon(@Nullable final Icon icon, int layer) {
    LayeredIcon layeredIcon = getLayeredIcon();
    layeredIcon.setIcon(icon, layer);
    if (hasIcons()) {
      myLabel.setIcon(layeredIcon);
    }
    else {
      myLabel.setIcon(null);
    }

    invalidateIfNeeded();
  }

  private LayeredIcon getLayeredIcon() {
    return myIcon;
  }

  public TabInfo getInfo() {
    return myInfo;
  }

  public void apply(UiDecorator.UiDecoration decoration) {
    if (decoration == null) {
      return;
    }

    if (decoration.getLabelFont() != null) {
      setFont(decoration.getLabelFont());
      getLabelComponent().setFont(decoration.getLabelFont());
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

  private static int getValue(int currentValue, int newValue) {
    return newValue != -1 ? newValue : currentValue;
  }

  public void setTabActions(ActionGroup group) {
    removeOldActionPanel();

    if (group == null) return;

    myActionPanel = new ActionPanel(myTabs, myInfo, e -> processMouseEvent(SwingUtilities.convertMouseEvent(e.getComponent(), e, this)),
                                    value -> setHovered(value));
    myActionPanel.setBorder(JBUI.Borders.empty(1, 0));
    toggleShowActions(false);

    add(myActionPanel, UISettings.getShadowInstance().getCloseTabButtonOnTheRight() ? BorderLayout.EAST : BorderLayout.WEST);

    myTabs.revalidateAndRepaint(false);
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

  private void setAttractionIcon(@Nullable Icon icon) {
    if (myIcon.getIcon(0) == null) {
      setIcon(null, 1);
      myOverlayedIcon = icon;
    }
    else {
      setIcon(icon, 1);
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

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);

    paintBackground(g);
  }

  private void paintBackground(Graphics g) {
    JBTabPainter painter = myTabs.getTabPainter();
    boolean isSelected = myInfo == myTabs.getSelectedInfo();

    Rectangle rect = new Rectangle(0, 0, getWidth(), getHeight());

    Graphics2D g2d = (Graphics2D)g;
    if (isSelected) {
      painter
        .paintSelectedTab(myTabs.getPosition(), g2d, rect, myTabs.getBorderThickness(), myInfo.getTabColor(), myTabs.isActiveTabs(myInfo),
                          myTabs.isHoveredTab(this));
    }
    else {
      painter.paintTab(myTabs.getPosition(), g2d, rect, myTabs.getBorderThickness(), myInfo.getTabColor(), myTabs.isHoveredTab(this));
    }
  }

  @Override
  protected void paintChildren(final Graphics g) {
    super.paintChildren(g);

    if (getLabelComponent().getParent() == null) {
      return;
    }

    final Rectangle textBounds = SwingUtilities.convertRectangle(getLabelComponent().getParent(), getLabelComponent().getBounds(), this);
    // Paint border around label if we got the focus
    if (isFocusOwner()) {
      g.setColor(UIUtil.getTreeSelectionBorderColor());
      UIUtil.drawDottedRectangle(g, textBounds.x, textBounds.y, textBounds.x + textBounds.width - 1, textBounds.y + textBounds.height - 1);
    }

    if (myOverlayedIcon == null) {
      return;
    }

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
      myActionPanel.toggleShowActions(show);
    }
  }

  public void setActionPanelVisible(boolean visible) {
    if (myActionPanel != null) {
      add(myActionPanel, UISettings.getShadowInstance().getCloseTabButtonOnTheRight() ? BorderLayout.EAST : BorderLayout.WEST);
      myActionPanel.setVisible(visible);
      if (visible) {
        myActionPanel.update();
      }
    }
  }

  @Override
  public String toString() {
    return myInfo.getText();
  }

  public void setTabEnabled(boolean enabled) {
    getLabelComponent().setEnabled(enabled);
  }

  public JComponent getLabelComponent() {
    return myLabel;
  }


  @Override
  public AccessibleContext getAccessibleContext() {
    if (accessibleContext == null) {
      accessibleContext = new AccessibleTabLabel();
    }
    return accessibleContext;
  }

  protected class AccessibleTabLabel extends AccessibleJPanel {
    @Override
    public String getAccessibleName() {
      String name = super.getAccessibleName();
      if (name == null && myLabel != null) {
        name = myLabel.getAccessibleContext().getAccessibleName();
      }
      return name;
    }

    @Override
    public String getAccessibleDescription() {
      String description = super.getAccessibleDescription();
      if (description == null && myLabel != null) {
        description = myLabel.getAccessibleContext().getAccessibleDescription();
      }
      return description;
    }

    @Override
    public AccessibleRole getAccessibleRole() {
      return AccessibleRole.PAGE_TAB;
    }
  }
}
