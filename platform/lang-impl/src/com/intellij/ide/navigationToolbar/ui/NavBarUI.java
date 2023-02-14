// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navigationToolbar.ui;

import com.intellij.ide.navigationToolbar.NavBarItem;
import com.intellij.ide.navigationToolbar.NavBarPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * NavBar painting is delegated to NavBarUI components. To change NavBar view you should
 * implement NavBarUI interface or override some methods of  {@code AbstractNavBarUI}.
 *
 * If NavBar is visible on IdeFrame it's structure is the following:
 *
 * <pre>
 * WrapperPanel____________________________________________________
 * |   __NavBarPanel_____________________|                        |
 * |  | NavBarItem \   NavBarItem\       | Toolbar (optional)     |
 * |  |____________/_____________/_______|                        |
 * |_____________________________________|________________________|
 * </pre>
 *
 *
 * @author Konstantin Bulenkov
 * @see NavBarUIManager
 * @see AbstractNavBarUI
 * @deprecated unused in ide.navBar.v2. If you do a change here, please also update v2 implementation
 */
@Deprecated
public interface NavBarUI {
  /**
   * Returns offset for NavBarPopup
   *
   * @param item nav bar item
   * @return offset for NavBarPopup
   */
  int getPopupOffset(@NotNull NavBarItem item);

  Insets getElementIpad(boolean isPopupElement);

  /**
   * @deprecated Use {@link #getElementPadding(NavBarItem)} instead.
   */
  @Deprecated(forRemoval = true)
  Insets getElementPadding();

  Insets getElementPadding(NavBarItem item);

  Font getElementFont(NavBarItem navBarItem);

  Insets getWrapperPanelInsets(Insets insets);

  /**
   * NavBarItem uses standard selection color from LaF. However, sometimes it looks very aggressive.
   * To handle this problem transparency is used. The selection color will be LaF list selection color with alpha
   *
   * @return alpha number from 0 to 255
   */
  short getSelectionAlpha();

  /**
   * NavBarItem offsets
   * @param item NavBar element
   * @return offsets
   */
  Dimension getOffsets(NavBarItem item);

  /**
   * Returns NavBarItem background
   * @param selected is element selected
   * @param focused is element focused (can be selected, but has no focus - while NavBarPopup showing)
   * @return NavBarItem background
   */
  Color getBackground(boolean selected, boolean focused);

  /**
   * Returns NavBarItem foreground
   * @param selected is element selected
   * @param focused is element focused (can be selected, but has no focus - while NavBarPopup showing)
   * @return NavBarItem foreground
   */
  @Nullable
  Color getForeground(boolean selected, boolean focused, boolean inactive);


  void doPaintWrapperPanel(Graphics2D g, Rectangle bounds, boolean mainToolbarVisible);

  void doPaintNavBarItem(Graphics2D g, NavBarItem item, NavBarPanel navbar);

  void clearItems();
}
