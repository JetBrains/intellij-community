/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.ui.tabs;

import com.intellij.ide.util.PropertiesComponent;

import javax.swing.*;

/**
 * @author pegov
 */
public class TabsUtil {
  public static final int TAB_VERTICAL_PADDING = PropertiesComponent.getInstance().getInt("TAB_VERTICAL_PADDING", 2);
  public static final int TABS_BORDER = PropertiesComponent.getInstance().getInt("TABS_BORDER", 1);
  
  public static final int ACTIVE_TAB_UNDERLINE_HEIGHT = PropertiesComponent.getInstance().getInt("ACTIVE_TAB_UNDERLINE_HEIGHT", 4);

  private TabsUtil() {
  }

  public static int getTabsHeight() {
    return new JLabel("XXX").getPreferredSize().height + 2 + TAB_VERTICAL_PADDING * 2 + TABS_BORDER * 2; 
  }
  
}
