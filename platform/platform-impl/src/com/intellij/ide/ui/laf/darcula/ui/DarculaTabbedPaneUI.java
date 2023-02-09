// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.JBColor;
import com.intellij.ui.RelativeFont;
import com.intellij.ui.paint.LinePainter2D;
import com.intellij.ui.paint.RectanglePainter2D;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicArrowButton;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import javax.swing.text.View;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeListener;
import java.lang.reflect.Field;
import java.util.*;

import static com.intellij.util.ui.JBUI.CurrentTheme.TabbedPane.*;

/**
 * @author Konstantin Bulenkov
 * @author Vassiliy Kudryashov
 */
public class DarculaTabbedPaneUI extends BasicTabbedPaneUI {
  private enum TabStyle {
    underline, fill
  }

  private TabStyle tabStyle;
  private PropertyChangeListener panePropertyListener;
  private ComponentListener paneComponentListener;
  private ChangeListener paneChangeListener;
  private MouseListener paneMouseListener;
  private MouseMotionListener paneMouseMotionListener;

  private JButton myShowHiddenTabsButton;
  private ArrayList<Component> myHiddenArrowButtons;
  private int hoverTab = -1;
  private boolean tabsOverlapBorder;
  private boolean useSelectedRectBackup = false;
  private boolean tabBackgroundOnlyForHover;
  private Color myTabHoverColor;

  private static final JBValue OFFSET = new JBValue.Float(1);

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    return new DarculaTabbedPaneUI();
  }

  @Override
  protected void installComponents() {
    super.installComponents();
    if (tabPane.getTabLayoutPolicy() == JTabbedPane.SCROLL_TAB_LAYOUT && !Boolean.getBoolean("use.basic.tabs.scrolling")) {
      myHiddenArrowButtons = new ArrayList<>(2);
      Arrays.asList(tabPane.getComponents()).forEach(child -> {
        if (child instanceof BasicArrowButton) {
          myHiddenArrowButtons.add(child);
        }
      });
      tabPane.setLayout(new WrappingLayout((TabbedPaneLayout)tabPane.getLayout()));
      tabPane.add(myShowHiddenTabsButton = new ShowHiddenTabsButton());
    }
    tabBackgroundOnlyForHover = Boolean.TRUE.equals(tabPane.getClientProperty("TabbedPane.tabBackgroundOnlyForHover"));

    if (tabPane.getClientProperty("TabbedPane.hoverColor") instanceof Color color) {
      myTabHoverColor = color;
    }
  }

  @Override
  protected void uninstallComponents() {
    super.uninstallComponents();
    if (myShowHiddenTabsButton != null) {
      tabPane.remove(myShowHiddenTabsButton);
    }
  }

  @Override
  public void uninstallUI(JComponent c) {
    if (tabPane.getLayout() instanceof WrappingLayout) {
      tabPane.setLayout(((WrappingLayout)tabPane.getLayout()).myDelegate);
    }
    super.uninstallUI(c);
    myHiddenArrowButtons = null;
    myShowHiddenTabsButton = null;
  }


  @Override
  protected void installDefaults() {
    super.installDefaults();

    modifyFontSize();

    Object rStyle = UIManager.get("TabbedPane.tabFillStyle");
    tabStyle = rStyle != null ? TabStyle.valueOf(rStyle.toString()) : TabStyle.underline;
    contentBorderInsets = tabPane.getTabLayoutPolicy() == JTabbedPane.WRAP_TAB_LAYOUT ? JBUI.insetsTop(1) : JBInsets.emptyInsets();
    tabsOverlapBorder = UIManager.getBoolean("TabbedPane.tabsOverlapBorder");
  }

  private void modifyFontSize() {
    if (SystemInfo.isMac || SystemInfo.isLinux) {
      Font font = UIManager.getFont("TabbedPane.font");
      tabPane.setFont(RelativeFont.NORMAL.fromResource("TabbedPane.fontSizeOffset", -1).derive(font));
    }
  }

  @Override
  protected void installListeners() {
    super.installListeners();

    panePropertyListener = evt -> {
      String propName = evt.getPropertyName();
      if ("JTabbedPane.hasFullBorder".equals(propName) || "tabLayoutPolicy".equals(propName)) {
        boolean fullBorder = tabPane.getClientProperty("JTabbedPane.hasFullBorder") == Boolean.TRUE;
        contentBorderInsets = (tabPane.getTabLayoutPolicy() == JTabbedPane.WRAP_TAB_LAYOUT) ?
                              fullBorder ? JBUI.insets(1) : JBUI.insetsTop(1) :
                              fullBorder ? JBUI.insets(0, 1, 1, 1) : JBInsets.emptyInsets();
        tabPane.revalidate();
        tabPane.repaint();
      }
      else if ("enabled".equals(propName)) {
        for (int ti = 0; ti < tabPane.getTabCount(); ti++) {
          Component tc = tabPane.getTabComponentAt(ti);
          if (tc != null) {
            tc.setEnabled(evt.getNewValue() == Boolean.TRUE);
          }
        }
        tabPane.repaint();
      }
      else if ("tabPlacement".equals(propName)) {
        int index = tabPane.getSelectedIndex();
        tabPane.setSelectedIndex(-1);
        SwingUtilities.invokeLater(() -> {
          tabPane.setSelectedIndex(index);
        });
      }
    };

    tabPane.addPropertyChangeListener(panePropertyListener);

    paneComponentListener = new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        ensureSelectedTabIsVisible();
      }
    };
    tabPane.addComponentListener(paneComponentListener);

    paneChangeListener = e -> ensureSelectedTabIsVisible();

    tabPane.addChangeListener(paneChangeListener);

    paneMouseListener = new MouseAdapter() {
      @Override
      public void mouseEntered(MouseEvent e) {
        hoverTab = tabForCoordinate(tabPane, e.getX(), e.getY());
        tabPane.repaint();
      }

      @Override
      public void mouseExited(MouseEvent e) {
        hoverTab = -1;
        tabPane.repaint();
      }
    };

    tabPane.addMouseListener(paneMouseListener);

    paneMouseMotionListener = new MouseMotionAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        hoverTab = tabForCoordinate(tabPane, e.getX(), e.getY());
        tabPane.repaint();
      }
    };
    tabPane.addMouseMotionListener(paneMouseMotionListener);
  }

  @Override
  public int tabForCoordinate(JTabbedPane pane, int x, int y) {
    if (myShowHiddenTabsButton != null) {
      Point p = new Point(x, y);
      JViewport viewport = getScrollableTabViewport();
      if (viewport != null) {
        Point vpp = viewport.getLocation();
        Point viewp = viewport.getViewPosition();
        p.x = x - vpp.x + viewp.x;
        p.y = y - vpp.y + viewp.y;
      }
      x = p.x;
      y = p.y;
    }
    return super.tabForCoordinate(pane, x, y);
  }


  @Override
  protected void uninstallListeners() {
    super.uninstallListeners();
    if (panePropertyListener != null) {
      tabPane.removePropertyChangeListener(panePropertyListener);
    }

    if (paneComponentListener != null) {
      tabPane.removeComponentListener(paneComponentListener);
    }

    if (paneChangeListener != null) {
      tabPane.removeChangeListener(paneChangeListener);
    }

    if (paneMouseListener != null) {
      tabPane.removeMouseListener(paneMouseListener);
    }

    if (paneMouseMotionListener != null) {
      tabPane.removeMouseMotionListener(paneMouseMotionListener);
    }
  }

  private boolean isTopBottom() {
    return tabPane.getTabPlacement() == TOP || tabPane.getTabPlacement() == BOTTOM;
  }

  private void ensureSelectedTabIsVisible() {
    int index = tabPane.getSelectedIndex();
    JViewport viewport = getScrollableTabViewport();
    if (viewport == null || rects.length <= index || index < 0) return;
    Dimension viewSize = viewport.getViewSize();
    Rectangle viewRect = viewport.getViewRect();
    Rectangle tabRect = rects[index];
    if (viewRect.contains(tabRect)) return;
    Point tabViewPosition = new Point();
    int location;
    Dimension extentSize;
    if (isTopBottom()) {
      location = tabRect.x < viewRect.x ? tabRect.x : tabRect.x + tabRect.width - viewRect.width;
      viewport.setViewPosition(new Point(Math.max(0, Math.min(viewSize.width - viewRect.width, location)), tabRect.y));
      tabViewPosition.x = index == 0 ? 0 : tabRect.x;
      extentSize = new Dimension(viewSize.width - tabViewPosition.x, viewRect.height);
    }
    else {
      location = tabRect.y < viewRect.y ? tabRect.y : tabRect.y + tabRect.height - viewRect.height;
      viewport.setViewPosition(new Point(tabRect.x, Math.max(0, Math.min(viewSize.height - viewRect.height, location))));
      tabViewPosition.y = index == 0 ? 0 : tabRect.y;
      extentSize = new Dimension(viewRect.width, viewSize.height - tabViewPosition.y);
    }
    viewport.setExtentSize(extentSize);

    PointerInfo info = MouseInfo.getPointerInfo();
    if (info != null) {
      Point mouseLocation = info.getLocation();
      SwingUtilities.convertPointFromScreen(mouseLocation, tabPane);
      int oldHoverTab = hoverTab;
      hoverTab = tabForCoordinate(tabPane, mouseLocation.x, mouseLocation.y);
      if (oldHoverTab != hoverTab) {
        tabPane.repaint();
      }
    }
  }

  @Override
  protected Insets getContentBorderInsets(int tabPlacement) {
    Insets i = JBInsets.create(contentBorderInsets);
    rotateInsets(contentBorderInsets, i, tabPlacement);
    return i;
  }

  @Override
  protected void paintTabArea(Graphics g, int tabPlacement, int selectedIndex) {
    if (tabPane.getTabLayoutPolicy() == JTabbedPane.SCROLL_TAB_LAYOUT) {
      Rectangle bounds = g.getClipBounds();
      g.setColor(JBColor.namedColor("TabbedPane.contentAreaColor", 0xbfbfbf));

      int offset = getOffset();
      if (tabPlacement == LEFT || tabPlacement == RIGHT) {
        g.fillRect(bounds.x + bounds.width - offset, bounds.y, offset, bounds.y + bounds.height);
      }
      else {
        g.fillRect(bounds.x, bounds.y + bounds.height - offset, bounds.x + bounds.width, offset);
      }
    }
    super.paintTabArea(g, tabPlacement, selectedIndex);
  }

  @Override
  protected void paintTabBackground(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w, int h, boolean isSelected) {
    if (tabStyle == TabStyle.fill) {
      if (tabPane.isEnabled()) {
        g.setColor(isSelected ? ENABLED_SELECTED_COLOR : tabIndex == hoverTab ? getHoverColor() : tabPane.getBackground());
      }
      else {
        g.setColor(isSelected ? DISABLED_SELECTED_COLOR : tabPane.getBackground());
      }
    }
    else {
      // underline
      Color c = tabPane.getBackground();
      if (tabPane.isEnabled()) {
        if (tabPane.hasFocus() && isSelected) {
          c = FOCUS_COLOR;
        }
        else if (tabIndex == hoverTab) {
          c = getHoverColor();
        }
      }

      g.setColor(c);
    }

    if (tabBackgroundOnlyForHover && tabIndex != hoverTab) {
      return;
    }

    if (tabPane.getTabLayoutPolicy() == JTabbedPane.SCROLL_TAB_LAYOUT) {
      if (tabPlacement == LEFT || tabPlacement == RIGHT) {
        w -= getOffset();
      }
      else {
        h -= getOffset();
      }
    }

    g.fillRect(x, y, w, h);
  }

  private @NotNull Color getHoverColor() {
    return myTabHoverColor == null ? HOVER_COLOR : myTabHoverColor;
  }

  @Override
  protected void paintText(Graphics g, int tabPlacement, Font font, FontMetrics metrics, int tabIndex,
                           String title, Rectangle textRect, boolean isSelected) {

    View v = getTextViewForTab(tabIndex);
    if (v != null || tabPane.isEnabled() && tabPane.isEnabledAt(tabIndex)) {
      super.paintText(g, tabPlacement, font, metrics, tabIndex, title, textRect, isSelected);
    }
    else { // tab disabled
      int mnemIndex = tabPane.getDisplayedMnemonicIndexAt(tabIndex);

      g.setFont(font);
      g.setColor(DISABLED_TEXT_COLOR);
      UIUtilities.drawStringUnderlineCharAt(tabPane, g, title, mnemIndex, textRect.x, textRect.y + metrics.getAscent());
    }
  }

  @Override
  protected void paintTabBorder(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w, int h, boolean isSelected) {
    if (isSelected && tabStyle == TabStyle.underline) {
      boolean wrap = tabPane.getTabLayoutPolicy() == JTabbedPane.WRAP_TAB_LAYOUT;
      switch (tabPlacement) {
        case LEFT -> {
          int offset = SELECTION_HEIGHT.get() - (wrap ? getOffset() : 0);
          paintUnderline(g, x + w - offset, y, SELECTION_HEIGHT.get(), h);
        }
        case RIGHT -> {
          int offset = wrap ? getOffset() : 0;
          paintUnderline(g, x - offset, y, SELECTION_HEIGHT.get(), h);
        }
        case BOTTOM -> {
          int offset = wrap ? getOffset() : 0;
          paintUnderline(g, x, y - offset, w, SELECTION_HEIGHT.get());
        }
        //case TOP,
        default -> {
          int offset = SELECTION_HEIGHT.get() - (wrap ? getOffset() : 0);
          paintUnderline(g, x, y + h - offset, w, SELECTION_HEIGHT.get());
        }
      }
    }
  }

  @Override
  protected int getTabLabelShiftY(int tabPlacement, int tabIndex, boolean isSelected) {
    int delta = SELECTION_HEIGHT.get();
    if (tabPane.getTabLayoutPolicy() == JTabbedPane.WRAP_TAB_LAYOUT) {
      delta -= getOffset();
    }

    return switch (tabPlacement) {
      case RIGHT, LEFT -> 0;
      case BOTTOM -> delta / 2;
      //case TOP,
      default -> -delta / 2;
    };
  }

  @Override
  protected int getTabLabelShiftX(int tabPlacement, int tabIndex, boolean isSelected) {
    int delta = SELECTION_HEIGHT.get();
    if (tabPane.getTabLayoutPolicy() == JTabbedPane.WRAP_TAB_LAYOUT) {
      delta -= getOffset();
    }

    return switch (tabPlacement) {
      case TOP, BOTTOM -> 0;
      case LEFT -> -delta / 2;
      //case RIGHT,
      default -> delta / 2;
    };
  }

  @Override
  protected int calculateTabWidth(int tabPlacement, int tabIndex, FontMetrics metrics) {
    return super.calculateTabWidth(tabPlacement, tabIndex, metrics) - 3; //remove magic constant '3' added by parent
  }

  @Override
  protected int calculateTabHeight(int tabPlacement, int tabIndex, int fontHeight) {
    int height = super.calculateTabHeight(tabPlacement, tabIndex, fontHeight) - 2; //remove magic constant '2' added by parent
    int minHeight = TAB_HEIGHT.get() - (tabPane.getTabLayoutPolicy() == JTabbedPane.WRAP_TAB_LAYOUT ? getOffset() : 0);
    return Math.max(height, minHeight);
  }

  @Override
  protected void paintContentBorderTopEdge(Graphics g, int tabPlacement, int selectedIndex, int x, int y, int w, int h) { }

  @Override
  protected void paintContentBorderLeftEdge(Graphics g, int tabPlacement, int selectedIndex, int x, int y, int w, int h) { }

  @Override
  protected void paintContentBorderRightEdge(Graphics g, int tabPlacement, int selectedIndex, int x, int y, int w, int h) { }

  @Override
  protected void paintContentBorderBottomEdge(Graphics g, int tabPlacement, int selectedIndex, int x, int y, int w, int h) { }

  @Override
  protected void paintFocusIndicator(Graphics g, int tabPlacement, Rectangle[] rects, int tabIndex, Rectangle iconRect, Rectangle textRect,
                                     boolean isSelected) { }

  @Override
  public void paint(Graphics g, JComponent c) {
    if (Boolean.getBoolean("use.basic.tabs.scrolling")) {
      super.paint(g, c);
      return;
    }
    int selectedIndex = tabPane.getSelectedIndex();
    int tabPlacement = tabPane.getTabPlacement();

    if (!tabPane.isValid()) {
      tabPane.validate();
    }

    if (!tabPane.isValid()) {
      TabbedPaneLayout layout = (TabbedPaneLayout)tabPane.getLayout();
      layout.calculateLayoutInfo();
    }

    if (tabsOverlapBorder) {
      paintContentBorder(g, tabPlacement, selectedIndex);
    }
    if (myShowHiddenTabsButton == null) { // WRAP_TAB_LAYOUT
      paintTabArea(g, tabPlacement, selectedIndex);
    }
    if (!tabsOverlapBorder) {
      paintContentBorder(g, tabPlacement, selectedIndex);
    }
  }

  @Nullable
  private JViewport getScrollableTabViewport() {
    Optional<JViewport> optional = UIUtil.findComponentsOfType(tabPane, JViewport.class).stream().filter(
      viewport -> "TabbedPane.scrollableViewport".equals(viewport.getName())).findFirst();
    return optional.orElse(null);
  }

  @Override
  protected Rectangle getTabBounds(int tabIndex, Rectangle dest) {
    dest.width = rects[tabIndex].width;
    dest.height = rects[tabIndex].height;

    JViewport viewport = getScrollableTabViewport();
    if (myShowHiddenTabsButton != null && viewport != null) {
      Point vpp = viewport.getLocation();
      Point viewp = viewport.getViewPosition();
      dest.x = rects[tabIndex].x + vpp.x - viewp.x;
      dest.y = rects[tabIndex].y + vpp.y - viewp.y;
    }
    else {
      dest.x = rects[tabIndex].x;
      dest.y = rects[tabIndex].y;
    }
    return dest;
  }

  private class ShowHiddenTabsButton extends JButton implements UIResource {
    private ShowHiddenTabsButton() {
      super(AllIcons.Actions.FindAndShowNextMatches);
      setToolTipText(IdeBundle.message("show.hidden.tabs"));
    }

    @Override
    protected void fireActionPerformed(ActionEvent event) {
      JViewport viewport = getScrollableTabViewport();
      if (viewport == null) return;
      Map<Integer, Rectangle> invisibleTabs = new LinkedHashMap<>();
      for (int i = 0; i < tabPane.getTabCount(); i++) {
        Rectangle rectangle = rects[i];
        if (!viewport.getViewRect().contains(rectangle)) invisibleTabs.put(i, rectangle);
      }
      JBPopupMenu menu = new JBPopupMenu();
      for (Map.Entry<Integer, Rectangle> entry : invisibleTabs.entrySet()) {
        final int index = entry.getKey();
        menu.add(new JMenuItem(tabPane.getTitleAt(index), tabPane.getIconAt(index)) {
          @Override
          protected void fireActionPerformed(ActionEvent event) {
            tabPane.setSelectedIndex(index);
          }
        });
      }
      menu.show(this, 0, getHeight());
    }
  }

  private class WrappingLayout extends TabbedPaneLayout {
    private final TabbedPaneLayout myDelegate;

    private WrappingLayout(TabbedPaneLayout delegate) {
      myDelegate = delegate;
    }

    @Override
    protected int preferredTabAreaHeight(int tabPlacement, int width) {
      return calculateMaxTabHeight(tabPlacement);
    }

    @Override
    protected int preferredTabAreaWidth(int tabPlacement, int height) {
      return calculateMaxTabWidth(tabPlacement);
    }

    @Override
    public void calculateLayoutInfo() {
      myDelegate.calculateLayoutInfo();
    }

    @Override
    public void addLayoutComponent(String name, Component comp) {
      myDelegate.addLayoutComponent(name, comp);
    }

    @Override
    public void removeLayoutComponent(Component comp) {
      myDelegate.removeLayoutComponent(comp);
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
      return myDelegate.preferredLayoutSize(parent);
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
      return myDelegate.minimumLayoutSize(parent);
    }

    @Override
    protected void padSelectedTab(int tabPlacement, int selectedIndex) {
    }

    @Override
    public void layoutContainer(Container parent) {
      myShowHiddenTabsButton.setBounds(new Rectangle());
      int selectedIndex = tabPane.getSelectedIndex();
      Rectangle selectedRectBackup;
      if (useSelectedRectBackup && selectedIndex != -1 && rects != null && rects.length > selectedIndex) {
        selectedRectBackup = new Rectangle(rects[selectedIndex]);
      }
      else {
        selectedRectBackup = null;
      }

      myDelegate.layoutContainer(parent);
      if (selectedRectBackup != null) {
        rects[selectedIndex] = selectedRectBackup;
      }

      useSelectedRectBackup = true;
      Field obj = ReflectionUtil.getDeclaredField(BasicTabbedPaneUI.class, "tabScroller");
      if (obj != null) {
        Object obj1 = ReflectionUtil.getFieldValue(obj, DarculaTabbedPaneUI.this);
        if (obj1 != null) {
          Field obj2 = ReflectionUtil.getDeclaredField(obj1.getClass(), "croppedEdge");
          if (obj2 != null) {
            Object obj3 = ReflectionUtil.getFieldValue(obj2, obj1);
            if (obj3 != null) {
              ReflectionUtil.resetField(obj3, "shape");
            }
          }
        }
      }

      if (myShowHiddenTabsButton != null && !myHiddenArrowButtons.isEmpty()) {
        Rectangle bounds = null;
        for (Component button : myHiddenArrowButtons) {
          bounds = bounds == null ? button.getBounds() : bounds.union(button.getBounds());
          button.setBounds(new Rectangle());
        }
        JViewport viewport = getScrollableTabViewport();
        // the last tab is selected, BasicTabbedPaneUI fails a bit
        if (bounds.isEmpty() && viewport != null) {
          Rectangle viewportBounds = viewport.getBounds();
          if (isTopBottom()) {
            int buttonsWidth = 2 * myHiddenArrowButtons.get(0).getPreferredSize().width;
            viewportBounds.width -= buttonsWidth;
            viewport.setBounds(viewportBounds);
            ensureSelectedTabIsVisible();
            bounds = new Rectangle(viewport.getX() + viewport.getWidth(), viewport.getY(), buttonsWidth, viewport.getHeight());
          }
          else {
            int buttonHeight = 2 * myHiddenArrowButtons.get(0).getPreferredSize().height;
            viewportBounds.height -= buttonHeight;
            viewport.setBounds(viewportBounds);
            ensureSelectedTabIsVisible();
            bounds = new Rectangle(viewport.getX(), viewport.getY() + viewport.getHeight(), viewport.getWidth(), buttonHeight);
          }
          myShowHiddenTabsButton.setBounds(bounds);
          return;
        }

        int placement = tabPane.getTabPlacement();
        int size;
        if (placement == TOP || placement == BOTTOM) {
          size = preferredTabAreaHeight(tabPane.getTabPlacement(), tabPane.getWidth());
        }
        else {
          size = preferredTabAreaWidth(tabPane.getTabPlacement(), tabPane.getWidth());
        }
        switch (placement) {
          case TOP:
            bounds.y -= size - bounds.height;
          case BOTTOM: {
            bounds.height = size;
            break;
          }
          case LEFT: {
            bounds.x -= size - bounds.width;
          }
          case RIGHT: {
            bounds.width = size;
            break;
          }
        }
        myShowHiddenTabsButton.setBounds(bounds);
      }
    }
  }

  protected int getOffset() {
    return OFFSET.get();
  }

  private void paintUnderline(Graphics g, int x, int y, int w, int h) {
    g.setColor(tabPane.isEnabled() ? ENABLED_SELECTED_COLOR : DISABLED_SELECTED_COLOR);
    double arc = SELECTION_ARC.get();

    if (arc == 0) {
      g.fillRect(x, y, w, h);
    }
    else {
      RectanglePainter2D.FILL.paint((Graphics2D)g, x, y, w, h, arc, LinePainter2D.StrokeType.INSIDE, 1.0,
                                    RenderingHints.VALUE_ANTIALIAS_ON);
    }
  }
}
