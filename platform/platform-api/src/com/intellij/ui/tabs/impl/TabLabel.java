// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  protected final SimpleColoredComponent label;

  private final LayeredIcon icon;
  private Icon overlayedIcon;

  private final TabInfo info;
  protected ActionPanel actionPanel;
  private boolean isCentered;
  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private boolean isCompressionEnabled;
  private boolean forcePaintBorders;

  private final Wrapper labelPlaceholder = new Wrapper(false);
  protected final JBTabsImpl tabs;

  public TabLabel(JBTabsImpl tabs, final TabInfo info) {
    super(false);

    this.tabs = tabs;
    this.info = info;

    label = createLabel();

    // Allow focus so that user can TAB into the selected TabLabel and then
    // navigate through the other tabs using the LEFT/RIGHT keys.
    setFocusable(ScreenReader.isActive());
    setOpaque(false);
    setLayout(new TabLabelLayout());

    labelPlaceholder.setOpaque(false);
    labelPlaceholder.setFocusable(false);
    label.setFocusable(false);
    add(labelPlaceholder, BorderLayout.CENTER);

    setAlignmentToCenter(true);

    icon = createLayeredIcon();

    addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(final MouseEvent e) {
        if (UIUtil.isCloseClick(e, MouseEvent.MOUSE_PRESSED)) {
          return;
        }
        if (JBTabsImpl.isSelectionClick(e) && TabLabel.this.info.isEnabled()) {
          TabInfo selectedInfo = TabLabel.this.tabs.getSelectedInfo();
          if (selectedInfo != TabLabel.this.info) {
            TabLabel.this.info.setPreviousSelection(selectedInfo);
          }
          Component c = SwingUtilities.getDeepestComponentAt(e.getComponent(), e.getX(), e.getY());
          if (c instanceof InplaceButton) {
            return;
          }
          TabLabel.this.tabs.select(info, true);
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
        TabLabel.this.info.setPreviousSelection(null);
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
            int index = TabLabel.this.tabs.getIndexOf(TabLabel.this.info);
            if (index >= 0) {
              e.consume();
              // Select the previous tab, then set the focus its TabLabel.
              TabInfo previous = TabLabel.this.tabs.findEnabledBackward(index, true);
              if (previous != null) {
                TabLabel.this.tabs.select(previous, false).doWhenDone(() -> TabLabel.this.tabs.getSelectedLabel().requestFocusInWindow());
              }
            }
          }
          else if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
            int index = TabLabel.this.tabs.getIndexOf(TabLabel.this.info);
            if (index >= 0) {
              e.consume();
              // Select the previous tab, then set the focus its TabLabel.
              TabInfo next = TabLabel.this.tabs.findEnabledForward(index, true);
              if (next != null) {
                // Select the next tab, then set the focus its TabLabel.
                TabLabel.this.tabs.select(next, false).doWhenDone(() -> TabLabel.this.tabs.getSelectedLabel().requestFocusInWindow());
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
      tabs.setHovered(this);
    }
    else {
      tabs.unHover(this);
    }
  }

  public boolean isHovered() {
    return tabs.isHoveredTab(this);
  }

  private boolean isSelected() {
    return tabs.getSelectedLabel() == this;
  }

  @Override
  public boolean isFocusable() {
    // we don't want the focus unless we are the selected tab
    return tabs.getSelectedLabel() == this && super.isFocusable();
  }

  private SimpleColoredComponent createLabel() {
    SimpleColoredComponent label = new SimpleColoredComponent() {
      @Override
      public Font getFont() {
        Font font = super.getFont();

        return (isFontSet() || !tabs.useSmallLabels()) ? font :
               RelativeFont.NORMAL.fromResource("EditorTabs.fontSizeOffset", -2, JBUIScale.scale(11f)).derive(StartupUiUtil.getLabelFont());
      }

      @Override
      protected Color getActiveTextColor(Color attributesColor) {
        TabPainterAdapter painterAdapter = tabs.tabPainterAdapter;
        TabTheme theme = painterAdapter.getTabTheme();
        Color foreground;
        if (tabs.getSelectedInfo() == info && (attributesColor == null || UIUtil.getLabelForeground().equals(attributesColor))) {
          foreground = tabs.isActiveTabs(info) ? theme.getUnderlinedTabForeground() : theme.getUnderlinedTabInactiveForeground();
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
    return info != null && info.isPinned();
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
    if (isCentered == toCenter && getLabelComponent().getParent() != null) return;

    setPlaceholderContent(toCenter, getLabelComponent());
  }

  protected void setPlaceholderContent(boolean toCenter, JComponent component) {
    labelPlaceholder.removeAll();

    JComponent content = toCenter ? new Centerizer(component, Centerizer.TYPE.BOTH) : new Centerizer(component, Centerizer.TYPE.VERTICAL);
    labelPlaceholder.setContent(content);

    isCentered = toCenter;
  }

  public void paintOffscreen(Graphics g) {
    synchronized (getTreeLock()) {
      validateTree();
    }
    doPaint(g);
  }

  @Override
  public void paint(final Graphics g) {
    if (tabs.isDropTarget(info)) {
      if (tabs.getDropSide() == -1) {
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
    return !Registry.is("ui.no.bangs.and.whistles", false) && tabs.isSingleRow();
  }

  protected void paintFadeout(final Graphics g) {
    Graphics2D g2d = (Graphics2D)g.create();
    try {
      Color tabBg = getEffectiveBackground();
      Color transparent = ColorUtil.withAlpha(tabBg, 0);
      int borderThickness = tabs.getBorderThickness();
      int width = JBUI.scale(MathUtil.clamp(Registry.intValue("ide.editor.tabs.fadeout.width", 10), 1, 200));

      Rectangle myRect = getBounds();
      myRect.height -= borderThickness + (isSelected() ? tabs.getTabPainter().getTabTheme().getUnderlineHeight() : borderThickness);
      // Fadeout for left part (needed only in top and bottom placements)
      if (myRect.x < 0) {
        Rectangle leftRect = new Rectangle(-myRect.x, borderThickness, width, myRect.height - 2 * borderThickness);
        paintGradientRect(g2d, leftRect, tabBg, transparent);
      }

      Rectangle contentRect = labelPlaceholder.getBounds();
      // Fadeout for right side before pin/close button (needed only in side placements and in squeezing layout)
      if (contentRect.width < labelPlaceholder.getPreferredSize().width + tabs.getTabHGap()) {
        Rectangle rightRect =
          new Rectangle(contentRect.x + contentRect.width - width, borderThickness, width, myRect.height - 2 * borderThickness);
        paintGradientRect(g2d, rightRect, transparent, tabBg);
      }
      // Fadeout for right side
      else if (tabs.getEffectiveLayout$intellij_platform_ide().isScrollable() &&
               myRect.width < getPreferredSize().width + tabs.getTabHGap()) {
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
    if (info.isPinned() && AdvancedSettings.getBoolean("editor.keep.pinned.tabs.on.left")) {
      @NotNull List<TabInfo> tabs = this.tabs.getTabs();
      for (int i = 0; i < tabs.size(); i++) {
        TabInfo cur = tabs.get(i);
        if (cur == info && i < tabs.size() - 1) {
          TabInfo next = tabs.get(i + 1);
          return !next.isPinned()
                 && this.tabs.getTabLabel(next).getY() == this.getY(); // check that cur and next are in the same row
        }
      }
    }
    return false;
  }

  public boolean isNextToLastPinned() {
    if (!info.isPinned() && AdvancedSettings.getBoolean("editor.keep.pinned.tabs.on.left")) {
      @NotNull List<TabInfo> tabs = this.tabs.getVisibleInfos();
      boolean wasPinned = false;
      for (TabInfo info : tabs) {
        if (wasPinned && info == this.info) return true;
        wasPinned = info.isPinned();
      }
    }
    return false;
  }

  public boolean isLastInRow() {
    List<TabInfo> infos = tabs.getVisibleInfos();
    for (int ind = 0; ind < infos.size() - 1; ind++) {
      TabLabel cur = tabs.getInfoToLabel().get(infos.get(ind));
      if (cur == this) {
        TabLabel next = tabs.getInfoToLabel().get(infos.get(ind + 1));
        return cur.getY() != next.getY();
      }
    }
    // can be empty in case of dragging tab label
    return !infos.isEmpty() && infos.get(infos.size() - 1) == info;
  }

  protected void handlePopup(final MouseEvent e) {
    if (e.getClickCount() != 1 || !e.isPopupTrigger() || PopupUtil.getPopupContainerFor(this) != null) return;

    if (e.getX() < 0 || e.getX() >= e.getComponent().getWidth() || e.getY() < 0 || e.getY() >= e.getComponent().getHeight()) return;

    String place = tabs.getPopupPlace();
    place = place != null ? place : ActionPlaces.UNKNOWN;
    tabs.setPopupInfo(info);

    final DefaultActionGroup toShow = new DefaultActionGroup();
    if (tabs.getPopupGroup() != null) {
      toShow.addAll(tabs.getPopupGroup());
      toShow.addSeparator();
    }

    JBTabsImpl tabs =
      (JBTabsImpl)JBTabsEx.NAVIGATION_ACTIONS_KEY.getData(DataManager.getInstance().getDataContext(e.getComponent(), e.getX(), e.getY()));
    if (tabs == this.tabs && this.tabs.getAddNavigationGroup()) {
      toShow.addAll(this.tabs.getNavigationActions());
    }

    if (toShow.getChildrenCount() == 0) return;

    this.tabs.setActivePopup(ActionManager.getInstance().createActionPopupMenu(place, toShow).getComponent());
    this.tabs.getActivePopup().addPopupMenuListener(this.tabs.getPopupListener());

    this.tabs.getActivePopup().addPopupMenuListener(this.tabs);
    JBPopupMenu.showByEvent(e, this.tabs.getActivePopup());
  }

  public void setText(final SimpleColoredText text) {
    label.change(() -> {
      label.clear();
      label.setIcon(hasIcons() ? icon : null);

      if (text != null) {
        text.appendToComponent(label);
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

    if (actionPanel != null) {
      actionPanel.invalidate();
    }

    tabs.revalidateAndRepaint(false);
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
      label.setIcon(layeredIcon);
    }
    else {
      label.setIcon(null);
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
    return icon;
  }

  public TabInfo getInfo() {
    return info;
  }

  public final void apply(@NotNull UiDecorator.UiDecoration decoration) {
    if (decoration.getLabelFont() != null) {
      setFont(decoration.getLabelFont());
      getLabelComponent().setFont(decoration.getLabelFont());
    }

    MergedUiDecoration resultDec = mergeUiDecorations(decoration, JBTabsImpl.defaultDecorator.getDecoration());
    setBorder(new EmptyBorder(resultDec.labelInsets()));
    label.setIconTextGap(resultDec.iconTextGap());

    Insets contentInsets = resultDec.contentInsetsSupplier().apply(getActionsPosition());
    labelPlaceholder.setBorder(new EmptyBorder(contentInsets));
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

    actionPanel = new ActionPanel(tabs, info,
                                    e -> processMouseEvent(SwingUtilities.convertMouseEvent(e.getComponent(), e, this)),
                                    value -> setHovered(value));
    toggleShowActions(false);
    add(actionPanel, isTabActionsOnTheRight() ? BorderLayout.EAST : BorderLayout.WEST);

    tabs.revalidateAndRepaint(false);
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
    return isShowTabActions() && actionPanel != null
           ? isTabActionsOnTheRight() ? ActionsPosition.RIGHT : ActionsPosition.LEFT
           : ActionsPosition.NONE;
  }

  public void enableCompressionMode(boolean enabled) {
    isCompressionEnabled = enabled;
  }

  private void removeOldActionPanel() {
    if (actionPanel != null) {
      actionPanel.getParent().remove(actionPanel);
      actionPanel = null;
    }
  }

  public boolean updateTabActions() {
    return actionPanel != null && actionPanel.update();
  }

  private void setAttractionIcon(@Nullable Icon icon) {
    if (this.icon.getIcon(0) == null) {
      setIcon(null, 1);
      overlayedIcon = icon;
    }
    else {
      setIcon(icon, 1);
      overlayedIcon = null;
    }
  }

  public boolean repaintAttraction() {
    if (!tabs.attractions.contains(info)) {
      if (getLayeredIcon().isLayerEnabled(1)) {
        getLayeredIcon().setLayerEnabled(1, false);
        setAttractionIcon(null);
        invalidateIfNeeded();
        return true;
      }
      return false;
    }

    boolean needsUpdate = false;

    if (getLayeredIcon().getIcon(1) != info.getAlertIcon()) {
      setAttractionIcon(info.getAlertIcon());
      needsUpdate = true;
    }

    int maxInitialBlinkCount = 5;
    int maxRefireBlinkCount = maxInitialBlinkCount + 2;
    if (info.getBlinkCount() < maxInitialBlinkCount && info.isAlertRequested()) {
      getLayeredIcon().setLayerEnabled(1, !getLayeredIcon().isLayerEnabled(1));
      if (info.getBlinkCount() == 0) {
        needsUpdate = true;
      }
      info.setBlinkCount(info.getBlinkCount() + 1);

      if (info.getBlinkCount() == maxInitialBlinkCount) {
        info.resetAlertRequest();
      }

      repaint();
    }
    else {
      if (info.getBlinkCount() < maxRefireBlinkCount && info.isAlertRequested()) {
        getLayeredIcon().setLayerEnabled(1, !getLayeredIcon().isLayerEnabled(1));
        info.setBlinkCount(info.getBlinkCount() + 1);

        if (info.getBlinkCount() == maxRefireBlinkCount) {
          info.setBlinkCount(maxInitialBlinkCount);
          info.resetAlertRequest();
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
    tabs.tabPainterAdapter.paintBackground(this, g, tabs);
  }

  protected @NotNull Color getEffectiveBackground() {
    Color bg = tabs.getTabPainter().getBackgroundColor();
    Color customBg = tabs.getTabPainter().getCustomBackground(getInfo().getTabColor(), isSelected(),
                                                              tabs.isActiveTabs(getInfo()), isHovered());
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

    if (overlayedIcon == null) {
      return;
    }

    if (getLayeredIcon().isLayerEnabled(1)) {

      final int top = (getSize().height - overlayedIcon.getIconHeight()) / 2;

      overlayedIcon.paintIcon(this, g, textBounds.x - overlayedIcon.getIconWidth() / 2, top);
    }
  }

  public void setTabActionsAutoHide(final boolean autoHide) {
    if (actionPanel == null || actionPanel.isAutoHide() == autoHide) {
      return;
    }

    actionPanel.setAutoHide(autoHide);
  }

  public void toggleShowActions(boolean show) {
    if (actionPanel != null) {
      actionPanel.toggleShowActions(show);
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
    return info.getText();
  }

  public void setTabEnabled(boolean enabled) {
    getLabelComponent().setEnabled(enabled);
  }

  public JComponent getLabelComponent() {
    return label;
  }

  @Override
  public String getToolTipText(MouseEvent event) {
    Point pointInLabel = new RelativePoint(event).getPoint(label);
    Icon icon = label.getIcon();
    int iconWidth = (icon != null ? icon.getIconWidth() : JBUI.scale(16));
    if ((label.getVisibleRect().width >= iconWidth * 2 || !UISettings.getInstance().getShowTabsTooltips())
        && label.findFragmentAt(pointInLabel.x) == SimpleColoredComponent.FRAGMENT_ICON) {
      String toolTip = this.icon.getToolTip(false);
      if (toolTip != null) {
        return Strings.capitalize(toolTip);
      }
    }
    return super.getToolTipText(event);
  }

  @Override
  public @Nullable Object getData(@NotNull String dataId) {
    if (info.getComponent() instanceof DataProvider provider) {
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
      if (name == null && label != null) {
        name = label.getAccessibleContext().getAccessibleName();
      }
      return name;
    }

    @Override
    public String getAccessibleDescription() {
      String description = super.getAccessibleDescription();
      if (description == null && label != null) {
        description = label.getAccessibleContext().getAccessibleDescription();
      }
      return description;
    }

    @Override
    public AccessibleRole getAccessibleRole() {
      return AccessibleRole.PAGE_TAB;
    }
  }

  public final class TabLabelLayout extends BorderLayout {
    private boolean myRightAlignment;

    public void setRightAlignment(boolean rightAlignment) {
      myRightAlignment = rightAlignment;
    }

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
        if (!info.isPinned() && tabs != null &&
            tabs.getEffectiveLayout$intellij_platform_ide().isScrollable() &&
            (ExperimentalUI.isNewUI() && !isHovered() || tabs.isHorizontalTabs()) &&
            isShowTabActions() && isTabActionsOnTheRight() &&
            parent.getWidth() < prefWidth) {
          layoutScrollable(parent);
        }
        else if (!info.isPinned() && isCompressionEnabled &&
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
      xOffset -= getShift(parent);
      xOffset = layoutComponent(xOffset, getLayoutComponent(CENTER), spaceTop, spaceHeight);
      layoutComponent(xOffset, getLayoutComponent(EAST), spaceTop, spaceHeight);
    }

    private int getShift(Container parent) {
      if (myRightAlignment) {
        int width = parent.getBounds().width;
        if (width > 0) {
          int shift = parent.getPreferredSize().width - width;
          if (shift > 0) {
            return shift;
          }
        }
      }
      return 0;
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
