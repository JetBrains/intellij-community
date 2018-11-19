// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs;

import com.intellij.util.ui.JBValue;

/**
 * @author pegov
 */
public class TabsUtil {
  public static final JBValue TAB_VERTICAL_PADDING = new JBValue.Float(2);

  private TabsUtil() {}

  public static int getTabsHeight(int baseHeight) {
    return baseHeight + TAB_VERTICAL_PADDING.get() * 2;
  }
}
