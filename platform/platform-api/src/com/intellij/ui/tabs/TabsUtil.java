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

import javax.swing.*;

/**
 * @author pegov
 */
public class TabsUtil {
  
  
  public static final int TAB_VERTICAL_PADDING = 3;
  public static final int TABS_BORDER = 1;  

  private TabsUtil() {
  }

  public static int getTabsHeight() {
    return TAB_VERTICAL_PADDING * 2 + new JLabel("XXX").getPreferredSize().height + 1 /* +1 ??? */; 
  }
  
}
