// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.*;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.IconUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Map;
import java.util.function.Supplier;

import static com.intellij.ide.actions.Switcher.SwitcherPanel.RECENT_LOCATIONS;
import static com.intellij.openapi.keymap.KeymapUtil.getActiveKeymapShortcuts;

/**
 * @author Konstantin Bulenkov
 */
class SwitcherToolWindowsListRenderer extends ColoredListCellRenderer<Object> {
  private final SpeedSearchBase mySpeedSearch;
  private final Map<ToolWindow, String> shortcuts;
  private final boolean myPinned;
  private Supplier<Boolean> myShowEdited;

  private boolean hide = false;

  SwitcherToolWindowsListRenderer(SpeedSearchBase speedSearch,
                                  Map<ToolWindow, String> shortcuts,
                                  boolean pinned,
                                  @NotNull Supplier<Boolean> showEdited) {
    mySpeedSearch = speedSearch;
    this.shortcuts = shortcuts;
    myPinned = pinned;
    myShowEdited = showEdited;
  }

  @Override
  protected void customizeCellRenderer(@NotNull JList<?> list,
                                       Object value,
                                       int index,
                                       boolean selected,
                                       boolean hasFocus) {
    setBorder(value == RECENT_LOCATIONS
              ? JBUI.Borders.customLine(selected ? getBackground() : new JBColor(Gray._220, Gray._80), 1, 0, 0, 0)
              : JBUI.Borders.empty());

    String nameToMatch = "";
    if (value instanceof ToolWindow) {
      ToolWindow tw = ((ToolWindow)value);
      hide = false;
      setPaintFocusBorder(false);
      setIcon(getIcon(tw));

      nameToMatch = tw.getStripeTitle();
      String shortcut = shortcuts.get(tw);
      String name;
      if (myPinned || shortcut == null) {
        name = nameToMatch;
      }
      else {
        append(shortcut, new SimpleTextAttributes(SimpleTextAttributes.STYLE_UNDERLINE, null));
        name = ": " + nameToMatch;
      }

      append(name);
    }
    else if (value == RECENT_LOCATIONS) {
      String label = Switcher.SwitcherPanel.getRecentLocationsLabel(myShowEdited);
      nameToMatch = label;

      ShortcutSet shortcuts = getActiveKeymapShortcuts(RecentLocationsAction.RECENT_LOCATIONS_ACTION_ID);
      append(label);

      if (!myShowEdited.get()) {
        append(" ").append(KeymapUtil.getShortcutsText(shortcuts.getShortcuts()), SimpleTextAttributes.GRAY_ATTRIBUTES);
      }
    }

    if (mySpeedSearch != null && mySpeedSearch.isPopupActive()) {
      hide = mySpeedSearch.matchingFragments(nameToMatch) == null && !StringUtil.isEmpty(mySpeedSearch.getEnteredPrefix());
    }
  }

  @Override
  protected void doPaint(Graphics2D g) {
    GraphicsConfig config = new GraphicsConfig(g);
    if (hide) {
      g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.15f));
    }
    super.doPaint(g);
    config.restore();
  }

  private static Icon getIcon(ToolWindow toolWindow) {
    Icon icon = toolWindow.getIcon();
    if (icon == null) {
      return PlatformIcons.UI_FORM_ICON;
    }

    icon = IconUtil.toSize(icon, JBUIScale.scale(16), JBUIScale.scale(16));
    return icon;
  }
}
