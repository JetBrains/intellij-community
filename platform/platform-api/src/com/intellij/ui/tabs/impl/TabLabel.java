// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tabs.impl;

import com.intellij.ide.DataManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.Strings;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.tabs.JBTabsEx;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.UiDecorator;
import com.intellij.ui.tabs.impl.themes.TabTheme;
import com.intellij.util.MathUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.*;
import com.intellij.util.ui.accessibility.ScreenReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class TabLabel extends JPanel implements Accessible, DataProvider {
  private static final Logger LOG = Logger.getInstance(TabLabel.class);
  private static final int MIN_WIDTH_TO_CROP_ICON = 39;

  // If this System property is set to true 'close' button would be shown on the left of text (it's on the right by default)
  protected final SimpleColoredComponent myLabel;

  private final LayeredIcon myIcon;
  private Icon myOverlayedIcon;

  private final TabInfo myInfo;
  protected ActionPanel myActionPanel;
  private boolean myCentered;
  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private boolean isCompressionEnabled;
  private boolean forcePaintBorders;

  private final Wrapper myLabelPlaceholder = new Wrapper(false);
  protected final JBTabsImpl myTabs;

  public TabLabel(JBTabsImpl tabs, final TabInfo info) {
    super(false);

    myTabs = tabs;
    myInfo = info;

    myLabel = createLabel();

    // Allow focus so that user can TAB into the selected TabLabel and then
    // navigate through the other tabs using the LEFT/RIGHT keys.
    setFocusable(ScreenReader.isActive());
    setOpaque(false);
    setLayout(new MyTabLabelLayout());

    myLabelPlaceholder.setOpaque(false);
    myLabelPlaceholder.setFocusable(false);
    myLabel.setFocusable(false);
    add(myLabelPlaceholder, BorderLayout.CENTER);

    setAlignmentToCenter(true);

    myIcon = createLayeredIcon();

    addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(final MouseEvent e) {
        if (UIUtil.isCloseClick(e, MouseEvent.MOUSE_PRESSED)) {
          return;
        }
        if (JBTabsImpl.isSelectionClick(e) && myInfo.isEnabled()) {
          TabInfo selectedInfo = myTabs.getSelectedInfo();
          if (selectedInfo != myInfo) {
            myInfo.setPreviousSelection(selectedInfo);
          }
          Component c = SwingUtilities.getDeepestComponentAt(e.getComponent(), e.getX(), e.getY());
          if (c instanceof InplaceButton) {
            return;
          }
          myTabs.select(info, true);
          JBPopup container = PopupUtil.getPopupContainerFor(TabLabel.this);
          if (container != null && ClientProperty.isTrue(container.getContent(), MorePopupAware.class)) {
            container.cancel();
          }
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

  protected void setHovered(boolean value) {
    if (isHovered() == value) return;
    if (value) {
      myTabs.setHovered(this);
    }
    else {
      myTabs.unHover(this);
    }
  }

  public boolean isHovered() {
    return myTabs.isHoveredTab(this);
  }

  private boolean isSelected() {
    return myTabs.getSelectedLabel() == this;
  }

  @Override
  public boolean isFocusable() {
    // we don't want the focus unless we are the selected tab
    return myTabs.getSelectedLabel() == this && super.isFocusable();
  }

  private SimpleColoredComponent createLabel() {
    SimpleColoredComponent label = new SimpleColoredComponent() {
      @Override
      public Font getFont() {
        Font font = super.getFont();

        return (isFontSet() || !myTabs.useSmallLabels()) ? font :
               RelativeFont.NORMAL.fromResource("EditorTabs.fontSizeOffset", -2, JBUIScale.scale(11f)).derive(StartupUiUtil.getLabelFont());
      }

      @Override
      protected Color getActiveTextColor(Color attributesColor) {
        TabPainterAdapter painterAdapter = myTabs.tabPainterAdapter;
        TabTheme theme = painterAdapter.getTabTheme();
        Color foreground;
        if (myTabs.getSelectedInfo() == myInfo && (attributesColor == null || UIUtil.getLabelForeground().equals(attributesColor))) {
          foreground = myTabs.isActiveTabs(myInfo) ? theme.getUnderlinedTabForeground() : theme.getUnderlinedTabInactiveForeground();
        }
        else {
          foreground = super.getActiveTextColor(attributesColor);
        }
        return editLabelForeground(foreground);
      }

      @Override
      protected void paintIcon(@NotNull Graphics g, @NotNull Icon icon, int offset) {
        Icon editedIcon = editIcon(icon);
        super.paintIcon(g, editedIcon, offset);
      }
    };
    label.setOpaque(false);
    label.setBorder(null);
    label.setIconOpaque(false);
    label.setIpad(JBInsets.emptyInsets());

    return label;
  }

  // Allows to edit the label foreground right before painting
  public @Nullable Color editLabelForeground(@Nullable Color baseForeground) {
    return baseForeground;
  }

  // Allows to edit the icon right before painting
  public @NotNull Icon editIcon(@NotNull Icon baseIcon) {
    return baseIcon;
  }

  public boolean isPinned() {
    return myInfo != null && myInfo.isPinned();
  }

  @Override
  public Dimension getPreferredSize() {
    Dimension size = getNotStrictPreferredSize();
    if (isPinned()) {
      size.width = Math.min(TabLayout.getMaxPinnedTabWidth(), size.width);
    }
    return size;
  }

  public Dimension getNotStrictPreferredSize() {
    return super.getPreferredSize();
  }

  public void setAlignmentToCenter(boolean toCenter) {
    if (myCentered == toCenter && getLabelComponent().getParent() != null) return;

    setPlaceholderContent(toCenter, getLabelComponent());
  }

  protected void setPlaceholderContent(boolean toCenter, JComponent component) {
    myLabelPlaceholder.removeAll();

    JComponent content = toCenter ? new Centerizer(component, Centerizer.TYPE.BOTH) : new Centerizer(component, Centerizer.TYPE.VERTICAL);
    myLabelPlaceholder.setContent(content);

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
    if (myTabs.isDropTarget(myInfo)) {
      if (myTabs.getDropSide() == -1) {
        g.setColor(JBUI.CurrentTheme.DragAndDrop.Area.BACKGROUND);
        g.fillRect(0, 0, getWidth(), getHeight());
      }
      return;
    }
    doPaint(g);
    if (shouldPaintFadeout()) {
      paintFadeout(g);
    }
  }

  protected boolean shouldPaintFadeout() {
    return !Registry.is("ui.no.bangs.and.whistles", false) && myTabs.isSingleRow();
  }

  protected void paintFadeout(final Graphics g) {
    Graphics2D g2d = (Graphics2D)g.create();
    try {
      Color tabBg = getEffectiveBackground();
      Color transparent = ColorUtil.withAlpha(tabBg, 0);
      int borderThickness = myTabs.getBorderThickness();
      int width = JBUI.scale(MathUtil.clamp(Registry.intValue("ide.editor.tabs.fadeout.width", 10), 1, 200));

      Rectangle myRect = getBounds();
      myRect.height -= borderThickness + (isSelected() ? myTabs.getTabPainter().getTabTheme().getUnderlineHeight() : borderThickness);
      // Fadeout for left part (needed only in top and bottom placements)
      if (myRect.x < 0) {
        Rectangle leftRect = new Rectangle(-myRect.x, borderThickness, width, myRect.height - 2 * borderThickness);
        paintGradientRect(g2d, leftRect, tabBg, transparent);
      }

      Rectangle contentRect = myLabelPlaceholder.getBounds();
      // Fadeout for right side before pin/close button (needed only in side placements and in squeezing layout)
      if (contentRect.width < myLabelPlaceholder.getPreferredSize().width + myTabs.getTabHGap()) {
        Rectangle rightRect =
          new Rectangle(contentRect.x + contentRect.width - width, borderThickness, width, myRect.height - 2 * borderThickness);
        paintGradientRect(g2d, rightRect, transparent, tabBg);
      }
      // Fadeout for right side
      else if (myTabs.getEffectiveLayout$intellij_platform_ide().isScrollable() &&
               myRect.width < getPreferredSize().width + myTabs.getTabHGap()) {
        Rectangle rightRect = new Rectangle(myRect.width - width, borderThickness, width, myRect.height - 2 * borderThickness);
        paintGradientRect(g2d, rightRect, transparent, tabBg);
      }
    }
    finally {
      g2d.dispose();
    }
  }

  private static void paintGradientRect(Graphics2D g, Rectangle rect, Color fromColor, Color toColor) {
    g.setPaint(new GradientPaint(rect.x, rect.y, fromColor, rect.x + rect.width, rect.y, toColor));
    g.fill(rect);
  }

  private void doPaint(final Graphics g) {
    super.paint(g);
  }

  public boolean isLastPinned() {
    if (myInfo.isPinned() && AdvancedSettings.getBoolean("editor.keep.pinned.tabs.on.left")) {
      @NotNull List<TabInfo> tabs = myTabs.getTabs();
      for (int i = 0; i < tabs.size(); i++) {
        TabInfo cur = tabs.get(i);
        if (cur == myInfo && i < tabs.size() - 1) {
          TabInfo next = tabs.get(i + 1);
          return !next.isPinned()
                 && myTabs.getTabLabel(next).getY() == this.getY(); // check that cur and next are in the same row
        }
      }
    }
    return false;
  }

  public boolean isNextToLastPinned() {
    if (!myInfo.isPinned() && AdvancedSettings.getBoolean("editor.keep.pinned.tabs.on.left")) {
      @NotNull List<TabInfo> tabs = myTabs.getVisibleInfos();
      boolean wasPinned = false;
      for (TabInfo info : tabs) {
        if (wasPinned && info == myInfo) return true;
        wasPinned = info.isPinned();
      }
    }
    return false;
  }

  public boolean isLastInRow() {
    List<TabInfo> infos = myTabs.getVisibleInfos();
    for (int ind = 0; ind < infos.size() - 1; ind++) {
      TabLabel cur = myTabs.getInfoToLabel().get(infos.get(ind));
      if (cur == this) {
        TabLabel next = myTabs.getInfoToLabel().get(infos.get(ind + 1));
        return cur.getY() != next.getY();
      }
    }
    // can be empty in case of dragging tab label
    return !infos.isEmpty() && infos.get(infos.size() - 1) == myInfo;
  }

  protected void handlePopup(final MouseEvent e) {
    if (e.getClickCount() != 1 || !e.isPopupTrigger() || PopupUtil.getPopupContainerFor(this) != null) return;

    if (e.getX() < 0 || e.getX() >= e.getComponent().getWidth() || e.getY() < 0 || e.getY() >= e.getComponent().getHeight()) return;

    String place = myTabs.getPopupPlace();
    place = place != null ? place : ActionPlaces.UNKNOWN;
    myTabs.setPopupInfo(myInfo);

    final DefaultActionGroup toShow = new DefaultActionGroup();
    if (myTabs.getPopupGroup() != null) {
      toShow.addAll(myTabs.getPopupGroup());
      toShow.addSeparator();
    }

    JBTabsImpl tabs =
      (JBTabsImpl)JBTabsEx.NAVIGATION_ACTIONS_KEY.getData(DataManager.getInstance().getDataContext(e.getComponent(), e.getX(), e.getY()));
    if (tabs == myTabs && myTabs.getAddNavigationGroup()) {
      toShow.addAll(myTabs.getNavigationActions());
    }

    if (toShow.getChildrenCount() == 0) return;

    myTabs.setActivePopup(ActionManager.getInstance().createActionPopupMenu(place, toShow).getComponent());
    myTabs.getActivePopup().addPopupMenuListener(myTabs.getPopupListener());

    myTabs.getActivePopup().addPopupMenuListener(myTabs);
    JBPopupMenu.showByEvent(e, myTabs.getActivePopup());
  }

  public void setText(final SimpleColoredText text) {
    myLabel.change(() -> {
      myLabel.clear();
      myLabel.setIcon(hasIcons() ? myIcon : null);

      if (text != null) {
        text.appendToComponent(myLabel);
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

  public void setIcon(@Nullable Icon icon) {
    setIcon(icon, 0);
  }

  private boolean hasIcons() {
    for (Icon layer1 : getLayeredIcon().getAllLayers()) {
      if (layer1 != null) {
        return true;
      }
    }
    return false;
  }

  private void setIcon(@Nullable Icon icon, int layer) {
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

  protected @NotNull LayeredIcon createLayeredIcon() {
    return new LayeredIcon(2) {
      @Override
      public int getIconWidth() {
        int iconWidth = super.getIconWidth();
        int tabWidth = TabLabel.this.getWidth();
        int minTabWidth = JBUI.scale(MIN_WIDTH_TO_CROP_ICON);
        if (isCompressionEnabled && tabWidth < minTabWidth) {
          return Math.max(iconWidth - (minTabWidth - tabWidth), iconWidth / 2);
        }
        else {
          return iconWidth;
        }
      }

      @Override
      public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics g2 = g.create(x, y, getIconWidth(), getIconHeight());
        try {
          super.paintIcon(c, g2, 0, 0);
        }
        finally {
          g2.dispose();
        }
      }
    };
  }

  private LayeredIcon getLayeredIcon() {
    return myIcon;
  }

  public TabInfo getInfo() {
    return myInfo;
  }

  public final void apply(@NotNull UiDecorator.UiDecoration decoration) {
    if (decoration.getLabelFont() != null) {
      setFont(decoration.getLabelFont());
      getLabelComponent().setFont(decoration.getLabelFont());
    }

    MergedUiDecoration resultDec = mergeUiDecorations(decoration, JBTabsImpl.defaultDecorator.getDecoration());
    setBorder(new EmptyBorder(resultDec.labelInsets()));
    myLabel.setIconTextGap(resultDec.iconTextGap());

    Insets contentInsets = resultDec.contentInsetsSupplier().apply(getActionsPosition());
    myLabelPlaceholder.setBorder(new EmptyBorder(contentInsets));
  }

  public static MergedUiDecoration mergeUiDecorations(@NotNull UiDecorator.UiDecoration customDec,
                                                      @NotNull UiDecorator.UiDecoration defaultDec) {
                                  Function<ActionsPosition, Insets> contentInsetsSupplier = position -> {
      Insets def = Objects.requireNonNull(defaultDec.getContentInsetsSupplier()).apply(position);
      if (customDec.getContentInsetsSupplier() != null) {
        return mergeInsets(customDec.getContentInsetsSupplier().apply(position), def);
      }
      return def;
    };
    return new MergedUiDecoration(
      mergeInsets(customDec.getLabelInsets(), Objects.requireNonNull(defaultDec.getLabelInsets())),
      contentInsetsSupplier,
      ObjectUtils.notNull(customDec.getIconTextGap(), Objects.requireNonNull(defaultDec.getIconTextGap()))
    );
  }

  private static @NotNull Insets mergeInsets(@Nullable Insets custom, @NotNull Insets def) {
    if (custom != null) {
      return new Insets(getValue(def.top, custom.top), getValue(def.left, custom.left),
                        getValue(def.bottom, custom.bottom), getValue(def.right, custom.right));
    }
    return def;
  }

  private static int getValue(int currentValue, int newValue) {
    return newValue != -1 ? newValue : currentValue;
  }

  public void setTabActions(ActionGroup group) {
    removeOldActionPanel();
    if (group == null) return;

    myActionPanel = new ActionPanel(myTabs, myInfo,
                                    e -> processMouseEvent(SwingUtilities.convertMouseEvent(e.getComponent(), e, this)),
                                    value -> setHovered(value));
    toggleShowActions(false);
    add(myActionPanel, isTabActionsOnTheRight() ? BorderLayout.EAST : BorderLayout.WEST);

    myTabs.revalidateAndRepaint(false);
  }

  /**
   * @deprecated specify {@link com.intellij.ui.tabs.UiDecorator.UiDecoration#contentInsetsSupplier} instead
   */
  @Deprecated(forRemoval = true)
  protected int getActionsInset() {
    return !isTabActionsOnTheRight() || ExperimentalUI.isNewUI() ? 6 : 2;
  }

  protected boolean isShowTabActions() {
    return true;
  }

  protected boolean isTabActionsOnTheRight() {
    return true;
  }

  public @NotNull ActionsPosition getActionsPosition() {
    return isShowTabActions() && myActionPanel != null
           ? isTabActionsOnTheRight() ? ActionsPosition.RIGHT : ActionsPosition.LEFT
           : ActionsPosition.NONE;
  }

  public void enableCompressionMode(boolean enabled) {
    isCompressionEnabled = enabled;
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
    if (!myTabs.attractions.contains(myInfo)) {
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
    myTabs.tabPainterAdapter.paintBackground(this, g, myTabs);
  }

  protected @NotNull Color getEffectiveBackground() {
    Color bg = myTabs.getTabPainter().getBackgroundColor();
    Color customBg = myTabs.getTabPainter().getCustomBackground(getInfo().getTabColor(), isSelected(),
                                                                myTabs.isActiveTabs(getInfo()), isHovered());
    return customBg != null ? ColorUtil.alphaBlending(customBg, bg) : bg;
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

  public void setForcePaintBorders(boolean forcePaintBorders) {
    this.forcePaintBorders = forcePaintBorders;
  }

  public boolean isForcePaintBorders() {
    return forcePaintBorders;
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
  public String getToolTipText(MouseEvent event) {
    Point pointInLabel = new RelativePoint(event).getPoint(myLabel);
    Icon icon = myLabel.getIcon();
    int iconWidth = (icon != null ? icon.getIconWidth() : JBUI.scale(16));
    if ((myLabel.getVisibleRect().width >= iconWidth * 2 || !UISettings.getInstance().getShowTabsTooltips())
        && myLabel.findFragmentAt(pointInLabel.x) == SimpleColoredComponent.FRAGMENT_ICON) {
      String toolTip = myIcon.getToolTip(false);
      if (toolTip != null) {
        return Strings.capitalize(toolTip);
      }
    }
    return super.getToolTipText(event);
  }

  @Override
  public @Nullable Object getData(@NotNull String dataId) {
    if (myInfo.getComponent() instanceof DataProvider provider) {
      return provider.getData(dataId);
    }
    return null;
  }

  public enum ActionsPosition {
    RIGHT, LEFT, NONE
  }

  public record MergedUiDecoration(@NotNull Insets labelInsets,
                                   @NotNull Function<ActionsPosition, Insets> contentInsetsSupplier,
                                   int iconTextGap) {
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

  private final class MyTabLabelLayout extends BorderLayout {
    @Override
    public void addLayoutComponent(Component comp, Object constraints) {
      checkConstraints(constraints);
      super.addLayoutComponent(comp, constraints);
    }

    private static void checkConstraints(Object constraints) {
      if (NORTH.equals(constraints) || SOUTH.equals(constraints)) {
        LOG.warn(new IllegalArgumentException("constraints=" + constraints));
      }
    }

    @Override
    public void layoutContainer(Container parent) {
      int prefWidth = parent.getPreferredSize().width;
      synchronized (parent.getTreeLock()) {
        if (!myInfo.isPinned() && myTabs != null &&
            myTabs.getEffectiveLayout$intellij_platform_ide().isScrollable() &&
            (ExperimentalUI.isNewUI() && !isHovered() || myTabs.isHorizontalTabs()) &&
            isShowTabActions() && isTabActionsOnTheRight() &&
            parent.getWidth() < prefWidth) {
          layoutScrollable(parent);
        }
        else if (!myInfo.isPinned() && isCompressionEnabled &&
                 !isHovered() && !isSelected() &&
                 parent.getWidth() < prefWidth) {
          layoutCompressible(parent);
        }
        else {
          super.layoutContainer(parent);
        }
      }
    }

    private void layoutScrollable(Container parent) {
      int spaceTop = parent.getInsets().top;
      int spaceLeft = parent.getInsets().left;
      int spaceBottom = parent.getHeight() - parent.getInsets().bottom;
      int spaceHeight = spaceBottom - spaceTop;

      int xOffset = spaceLeft;
      xOffset = layoutComponent(xOffset, getLayoutComponent(WEST), spaceTop, spaceHeight);
      xOffset = layoutComponent(xOffset, getLayoutComponent(CENTER), spaceTop, spaceHeight);
      layoutComponent(xOffset, getLayoutComponent(EAST), spaceTop, spaceHeight);
    }

    private int layoutComponent(int xOffset, Component component, int spaceTop, int spaceHeight) {
      if (component != null) {
        int prefWestWidth = component.getPreferredSize().width;
        component.setBounds(xOffset, spaceTop, prefWestWidth, spaceHeight);
        xOffset += prefWestWidth + getHgap();
      }
      return xOffset;
    }

    private void layoutCompressible(Container parent) {
      Insets insets = parent.getInsets();
      int height = parent.getHeight() - insets.bottom - insets.top;
      int curX = insets.left;
      int maxX = parent.getWidth() - insets.right;

      Component left = getLayoutComponent(WEST);
      Component center = getLayoutComponent(CENTER);
      Component right = getLayoutComponent(EAST);

      if (left != null) {
        left.setBounds(0, 0, 0, 0);
        int decreasedLen = parent.getPreferredSize().width - parent.getWidth();
        int width = Math.max(left.getPreferredSize().width - decreasedLen, 0);
        curX += width;
      }

      if (center != null) {
        int width = Math.min(center.getPreferredSize().width, maxX - curX);
        center.setBounds(curX, insets.top, width, height);
      }

      if (right != null) {
        right.setBounds(0, 0, 0, 0);
      }
    }
  }
}
