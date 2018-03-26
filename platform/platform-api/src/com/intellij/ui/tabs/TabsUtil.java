// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs;

import javax.swing.*;

/**
 * @author pegov
 */
public class TabsUtil {
  public static final int TAB_VERTICAL_PADDING = 2;
  public static final int TABS_BORDER = 1;

  public static final int ACTIVE_TAB_UNDERLINE_HEIGHT = 4;

  private TabsUtil() {
  }

  public static int getTabsHeight() {
    return getTabsHeight(TAB_VERTICAL_PADDING);
  }

  public static int getTabsHeight(int verticalPadding) {
    return new JLabel("XXX").getPreferredSize().height + 2 + verticalPadding * 2 + TABS_BORDER * 2;
  }
}
