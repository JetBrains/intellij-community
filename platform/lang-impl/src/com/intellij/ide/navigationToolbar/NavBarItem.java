// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navigationToolbar;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.navigationToolbar.ui.NavBarUI;
import com.intellij.ide.util.treeView.TreeAnchorizer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiElement;
import com.intellij.ui.*;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.PlatformIcons;
import com.intellij.util.SlowOperations;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.AccessibleAction;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.List;

import static com.intellij.ui.SimpleTextAttributes.STYLE_PLAIN;

/**
 * @author Konstantin Bulenkov
 */
public final class NavBarItem extends SimpleColoredComponent implements DataProvider, Disposable {
  private final @Nls String myText;
  private final SimpleTextAttributes myAttributes;
  private final int myIndex;
  private final Icon myIcon;
  private final NavBarPanel myPanel;
  private final Object myObject;
  private final boolean isPopupElement;
  private final NavBarUI myUI;
  private boolean mouseHovered;

  public static final Icon CHEVRON_ICON = AllIcons.General.ChevronRight;

  public NavBarItem(NavBarPanel panel, Object object, int idx, Disposable parent) {
    this(panel, object, idx, parent, false);
  }

  public NavBarItem(NavBarPanel panel, Object object, int idx, Disposable parent, boolean inPopup) {
    myPanel = panel;
    myUI = panel.getNavBarUI();
    myObject = object == null ? null : TreeAnchorizer.getService().createAnchor(object);
    myIndex = idx;
    isPopupElement = idx == -1;

    if (object != null) {
      NavBarPresentation presentation = myPanel.getPresentation();
      myText = presentation.getPresentableText(object, inPopup);
      myIcon = presentation.getIcon(object);
      myAttributes = presentation.getTextAttributes(object, false);
    }
    else {
      myText = IdeBundle.message("navigation.bar.item.sample");
      myIcon = PlatformIcons.FOLDER_ICON;
      myAttributes = SimpleTextAttributes.REGULAR_ATTRIBUTES;
    }

    Disposer.register(parent == null ? panel : parent, this);

    setOpaque(false);
    setIpad(myUI.getElementIpad(isPopupElement));

    if (ExperimentalUI.isNewUI()) {
      setIconTextGap(JBUIScale.scale(4));
    }

    if (!isPopupElement) {
      setMyBorder(null);
      setBorder(null);
      setPaintFocusBorder(false);
      setIconOpaque(false);
      if (myPanel.allowNavItemsFocus()) {
        // Take ownership of Tab/Shift-Tab navigation (to move focus out of nav bar panel), as
        // navigation between items is handled by the Left/Right cursor keys. This is similar
        // to the behavior a JRadioButton contained inside a GroupBox.
        setFocusTraversalKeysEnabled(false);
        setFocusable(true);
        addKeyListener(new KeyHandler());
        addFocusListener(new FocusHandler());
      }
    }
    else {
      setIconOpaque(true);
      setFocusBorderAroundIcon(true);
    }

    Font font = getFont();
    setFont(RelativeFont.NORMAL.fromResource("NavBar.fontSizeOffset", 0).derive(font));
    update();
  }

  public NavBarItem(NavBarPanel panel, Object object, Disposable parent, boolean inPopup) {
    this(panel, object, -1, parent, inPopup);
  }

  public Object getObject() {
    return myObject == null ? null : SlowOperations.allowSlowOperations(() -> TreeAnchorizer.getService().retrieveElement(myObject));
  }

  public SimpleTextAttributes getAttributes() {
    return myAttributes;
  }

  @NotNull
  public String getText() {
    return myText;
  }

  @Override
  public Font getFont() {
    return myUI == null ? super.getFont() : myUI.getElementFont(this);
  }

  void update() {
    clear();

    setIcon(myIcon);

    final boolean focused = isFocusedOrPopupElement();
    final boolean selected = isSelected();

    setBackground(myUI.getBackground(selected, focused));

    Color fg;
    Color bg = getBackground();

    if (ExperimentalUI.isNewUI()) {
      if (isMouseHover()) fg = JBUI.CurrentTheme.StatusBar.Breadcrumbs.HOVER_FOREGROUND;
      else if (selected && focused) fg = JBUI.CurrentTheme.StatusBar.Breadcrumbs.SELECTION_FOREGROUND;
      else if (selected && myPanel.isNodePopupActive() && !isPopupElement()) fg = JBUI.CurrentTheme.StatusBar.Breadcrumbs.SELECTION_INACTIVE_FOREGROUND;
      else if (isInFloatingMode()) fg = JBUI.CurrentTheme.StatusBar.Breadcrumbs.FLOATING_FOREGROUND;
      else if (isPopupElement()) fg = JBUI.CurrentTheme.List.foreground(selected, focused);
      else fg = JBUI.CurrentTheme.StatusBar.Breadcrumbs.FOREGROUND;
    }
    else {
      fg = myUI.getForeground(selected, focused, isInactive());
      if (fg == null) fg = myAttributes.getFgColor();
    }

    int style = myAttributes.getStyle();
    if (ExperimentalUI.isNewUI()) {
      style = STYLE_PLAIN;
      //if (myAttributes.isWaved()) style |= STYLE_WAVED;
    }

    append(myText, new SimpleTextAttributes(bg, fg, ExperimentalUI.isNewUI() ? null : myAttributes.getWaveColor(), style));
  }

  public boolean isInactive() {
    final NavBarModel model = myPanel.getModel();
    return model.getSelectedIndex() < myIndex && model.getSelectedIndex() != -1 && !myPanel.isUpdating();
  }

  public boolean isPopupElement() {
    return isPopupElement;
  }

  @Override
  protected void doPaint(Graphics2D g) {
    if (isPopupElement) {
      super.doPaint(g);
    }
    else {
      myUI.doPaintNavBarItem(g, this, myPanel);
    }
  }

  public int doPaintText(Graphics2D g, int offset) {
    return super.doPaintText(g, offset, false);
  }

  public boolean isLastElement() {
    return myIndex == myPanel.getModel().size() - 1;
  }

  public boolean isFirstElement() {
    return myIndex == 0;
  }

  @Override
  public void setOpaque(boolean isOpaque) {
    super.setOpaque(false);
  }

  @NotNull
  @Override
  public Dimension getPreferredSize() {
    final Dimension size = super.getPreferredSize();
    final Dimension offsets = myUI.getOffsets(this);
    int width = size.width + offsets.width;

    if (ExperimentalUI.isNewUI() && !isFirstElement() && !isPopupElement) {
      width += CHEVRON_ICON.getIconWidth() + JBUI.CurrentTheme.StatusBar.Breadcrumbs.CHEVRON_INSET.get();
    }

    if (!needPaintIcon() && myIcon != null) {
      width -= myIcon.getIconWidth() + (ExperimentalUI.isNewUI() ? getIconTextGap() : 0);
    }
    return new Dimension(width, size.height + offsets.height);
  }

  @DirtyUI
  public boolean needPaintIcon() {
    if (Registry.is("navBar.show.icons") || isPopupElement || isLastElement()) {
      return true;
    }
    Object object = getObject();
    return object instanceof PsiElement && ((PsiElement)object).getContainingFile() != null;
  }

  @NotNull
  @Override
  public Dimension getMinimumSize() {
    return getPreferredSize();
  }

  private boolean isFocusedOrPopupElement() {
    return isFocused() || isPopupElement;
  }

  public boolean isFocused() {
    if (myPanel.allowNavItemsFocus()) {
      return UIUtil.isFocusAncestor(myPanel) && !myPanel.isNodePopupActive();
    } else {
      return myPanel.hasFocus() && !myPanel.isNodePopupActive();
    }
  }

  public boolean isSelected() {
    final NavBarModel model = myPanel.getModel();
    return isPopupElement ? myPanel.isSelectedInPopup(getObject()) : model.getSelectedIndex() == myIndex;
  }

  @Override
  protected boolean shouldDrawBackground() {
    return isSelected() && isFocusedOrPopupElement();
  }

  @Override
  public void dispose() { }

  public boolean isNextSelected() {
    return myIndex == myPanel.getModel().getSelectedIndex() - 1;
  }

  @Nullable
  @Override
  public Object getData(@NotNull String dataId) {
    return myPanel.getDataImpl(dataId, this, () -> JBIterable.of(getObject()));
  }

  public int getIndex() {
    return myIndex;
  }

  public void setMouseHover(boolean hovered) {
    mouseHovered = hovered;
    update();
  }

  public boolean isMouseHover() {
    return mouseHovered;
  }

  public boolean isInFloatingMode() {
    return myPanel.isInFloatingMode();
  }

  @Override
  public AccessibleContext getAccessibleContext() {
    if (accessibleContext == null) {
      accessibleContext = new AccessibleNavBarItem();
    }
    return accessibleContext;
  }

  protected class AccessibleNavBarItem extends AccessibleSimpleColoredComponent implements AccessibleAction {
    @Override
    public AccessibleRole getAccessibleRole() {
      if (!isPopupElement()) {
        return AccessibleRole.PUSH_BUTTON;
      }
      return super.getAccessibleRole();
    }

    @Override
    public AccessibleAction getAccessibleAction() {
      return this;
    }

    @Override
    public int getAccessibleActionCount() {
      return !isPopupElement() ? 1 : 0;
    }

    @Override
    public String getAccessibleActionDescription(int i) {
      if (i == 0 && !isPopupElement()) {
        return UIManager.getString("AbstractButton.clickText");
      }
      return null;
    }

    @Override
    public boolean doAccessibleAction(int i) {
      if (i == 0 && !isPopupElement()) {
        myPanel.getModel().setSelectedIndex(myIndex);
      }
      return false;
    }
  }

  private class KeyHandler extends KeyAdapter {
    // This listener checks if the key event is a KeyEvent.VK_TAB
    // or shift + KeyEvent.VK_TAB event, consume the event
    // if so and move the focus to next/previous component after/before
    // the containing NavBarPanel.
    @Override
    public void keyPressed(KeyEvent e) {
      if (e.getKeyCode() == KeyEvent.VK_TAB) {
        // Check source is a nav bar item
        if (e.getSource() instanceof NavBarItem) {
          e.consume();
          jumpToNextComponent(!e.isShiftDown());
        }
      }
    }

    void jumpToNextComponent(boolean next) {
      // The base will be first or last NavBarItem in the NavBarPanel
      NavBarItem focusBase = null;
      List<NavBarItem> items = myPanel.getItems();
      if (items.size() > 0) {
        if (next) {
          focusBase = items.get(items.size() - 1);
        } else {
          focusBase = items.get(0);
        }
      }

      // Transfer focus
      if (focusBase != null){
        if (next) {
          KeyboardFocusManager.getCurrentKeyboardFocusManager().focusNextComponent(focusBase);
        } else {
          KeyboardFocusManager.getCurrentKeyboardFocusManager().focusPreviousComponent(focusBase);
        }
      }
    }
  }

  private class FocusHandler implements FocusListener {
    @Override
    public void focusGained(FocusEvent e) {
      myPanel.fireNavBarItemFocusGained(e);
    }

    @Override
    public void focusLost(FocusEvent e) {
      myPanel.fireNavBarItemFocusLost(e);
    }
  }
}
