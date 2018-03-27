/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.util.Pass;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.*;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.tabs.JBTabsPosition;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.TabsUtil;
import com.intellij.ui.tabs.UiDecorator;
import com.intellij.ui.tabs.impl.table.TableLayout;
import com.intellij.util.PairConsumer;
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
import java.awt.image.BufferedImage;

public class TabLabel extends JPanel implements Accessible {
  // If this System property is set to true 'close' button would be shown on the left of text (it's on the right by default)
  private static final String BUTTON_ON_THE_LEFT_KEY = "closeTabButtonOnTheLeft";
  protected final SimpleColoredComponent myLabel;

  private final LayeredIcon myIcon;
  private Icon myOverlayedIcon;

  private final TabInfo myInfo;
  protected ActionPanel myActionPanel;
  private boolean myCentered;

  private final Wrapper myLabelPlaceholder = new Wrapper(false);
  protected final JBTabsImpl myTabs;

  private BufferedImage myInactiveStateImage;
  private Rectangle myLastPaintedInactiveImageBounds;

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
  public boolean isFocusable() {
    // We don't want the focus unless we are the selected tab.
    if (myTabs.getSelectedLabel() != this)
      return false;

    return super.isFocusable();
  }

  private SimpleColoredComponent createLabel(final JBTabsImpl tabs) {
    SimpleColoredComponent label = new SimpleColoredComponent() {
      @Override
      protected boolean shouldDrawMacShadow() {
        return SystemInfo.isMac || UIUtil.isUnderDarcula();
      }

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
      protected void doPaint(Graphics2D g) {
        Rectangle clip = getVisibleRect();
        if (getPreferredSize().width <= clip.width + 2) {
          super.doPaint(g);
          return;
        }
        int dimSize = 10;
        int dimStep = 2;
        Composite oldComposite = g.getComposite();
        Shape oldClip = g.getClip();
        try {
          g.setClip(clip.x, clip.y, Math.max(0, clip.width - dimSize), clip.height);
          super.doPaint(g);

          for (int x = clip.x + clip.width - dimSize; x < clip.x + clip.width; x+=dimStep) {
            g.setClip(x, clip.y, dimStep, clip.height);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1 - ((float)x - (clip.x + clip.width - dimSize)) / dimSize));
            super.doPaint(g);
          }
        } finally {
          g.setComposite(oldComposite);
          g.setClip(oldClip);
        }
      }
    };
    label.setOpaque(false);
    label.setBorder(null);
    label.setIconTextGap(tabs.isEditorTabs() ? (!UISettings.getShadowInstance().getHideTabsIfNeed() ? 4 : 2) : new JLabel().getIconTextGap());
    label.setIconOpaque(false);
    label.setIpad(JBUI.emptyInsets());

    return label;
  }

  @Override
  public Insets getInsets() {
    Insets insets = super.getInsets();
    if (myTabs.isEditorTabs() && UISettings.getShadowInstance().getShowCloseButton()) {
      if (Boolean.getBoolean(BUTTON_ON_THE_LEFT_KEY)) {
        insets.left = 3;
      }
      else {
        insets.right = 3;
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

    if (myTabs.getSelectedInfo() != myInfo) {
      doPaint(g);
    }
  }

  public void paintImage(Graphics g) {
    final Rectangle b = getBounds();
    final Graphics lG = g.create(b.x, b.y, b.width, b.height);
    try {
      lG.setColor(Color.red);
      doPaint(lG);
    }
    finally {
      lG.dispose();
    }
  }

  public void doTranslate(PairConsumer<Integer, Integer> consumer) {
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

    if (!myTabs.isDropTarget(myInfo)) {
      if (myTabs.getSelectedInfo() != myInfo) {
        consumer.consume(dX, dY);
      }
      else {
        consumer.consume(dXs, dYs);
      }
    }
  }

  private void doPaint(final Graphics g) {
    doTranslate((x, y) -> g.translate(x, y));

    final Composite oldComposite = ((Graphics2D)g).getComposite();
    //if (myTabs instanceof JBEditorTabs && !myTabs.isSingleRow() && myTabs.getSelectedInfo() != myInfo) {
    //  ((Graphics2D)g).setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.9f));
    //}
    super.paint(g);
    ((Graphics2D)g).setComposite(oldComposite);

    doTranslate((x2, y2) -> g.translate(-x2, -y2));
  }

  protected int getNonSelectedOffset() {
    if (myTabs.isEditorTabs() && (myTabs.isSingleRow() || ((TableLayout)myTabs.getEffectiveLayout()).isLastRow(getInfo()))) {
      return -myTabs.getActiveTabUnderlineHeight() / 2 + 1;
    }
    return 1;
  }

  protected int getSelectedOffset() {
    return getNonSelectedOffset();
  }

  @Override
  public Dimension getPreferredSize() {
    final Dimension size = super.getPreferredSize();
    size.height = TabsUtil.getTabsHeight();
    if (myActionPanel != null && !myActionPanel.isVisible()) {
      final Dimension actionPanelSize = myActionPanel.getPreferredSize();
      size.width += actionPanelSize.width;
    }

    final JBTabsPosition pos = myTabs.getTabsPosition();
    switch (pos) {
      case top:
      case bottom:
        if (myTabs.hasUnderline()) size.height += myTabs.getActiveTabUnderlineHeight() - 1;
        break;
      case left:
      case right:
        size.width += getSelectedOffset();
        break;
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

    JBTabsImpl tabs =
      JBTabsImpl.NAVIGATION_ACTIONS_KEY.getData(DataManager.getInstance().getDataContext(e.getComponent(), e.getX(), e.getY()));
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

    setInactiveStateImage(null);

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

    myActionPanel = new ActionPanel(myTabs, myInfo, new Pass<MouseEvent>() {
      @Override
      public void pass(final MouseEvent event) {
        final MouseEvent me = SwingUtilities.convertMouseEvent(event.getComponent(), event, TabLabel.this);
        processMouseEvent(me);
      }
    });

    toggleShowActions(false);

    add(myActionPanel, Boolean.getBoolean(BUTTON_ON_THE_LEFT_KEY) ? BorderLayout.WEST : BorderLayout.EAST);

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
  protected void paintChildren(final Graphics g) {
    super.paintChildren(g);

    if (getLabelComponent().getParent() == null)
      return;

    final Rectangle textBounds = SwingUtilities.convertRectangle(getLabelComponent().getParent(), getLabelComponent().getBounds(), this);
    // Paint border around label if we got the focus
    if (isFocusOwner()) {
      g.setColor(UIUtil.getTreeSelectionBorderColor());
      UIUtil.drawDottedRectangle(g, textBounds.x, textBounds.y, textBounds.x + textBounds.width - 1, textBounds.y + textBounds.height - 1);
    }

    if (myOverlayedIcon == null)
      return;

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
      myActionPanel.setVisible(visible);
    }
  }

  @Override
  public String toString() {
    return myInfo.getText();
  }

  public void setTabEnabled(boolean enabled) {
    getLabelComponent().setEnabled(enabled);
  }


  @Nullable
  public BufferedImage getInactiveStateImage(Rectangle effectiveBounds) {
    BufferedImage img = null;
    if (myLastPaintedInactiveImageBounds != null && myLastPaintedInactiveImageBounds.getSize().equals(effectiveBounds.getSize())) {
      img = myInactiveStateImage;
    }
    else {
      setInactiveStateImage(null);
    }
    myLastPaintedInactiveImageBounds = effectiveBounds;
    return img;
  }

  public void setInactiveStateImage(@Nullable BufferedImage img) {
    if (myInactiveStateImage != null && img != myInactiveStateImage) {
      myInactiveStateImage.flush();
    }
    myInactiveStateImage = img;
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
