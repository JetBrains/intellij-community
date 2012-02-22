/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.ui.tabs.impl.JBTabsImpl;

/**
 * Shows the popup of all tabs when single row editor tab layout is used and all tabs don't fit on the screen.
 *
 * @author yole
 */
public class TabListAction extends AnAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    JBTabsImpl tabs = e.getData(JBTabsImpl.NAVIGATION_ACTIONS_KEY);
    if (tabs != null) {
      tabs.showMorePopup(null);
    }
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(isTabListAvailable(e));
  }

  private static boolean isTabListAvailable(AnActionEvent e) {
    JBTabsImpl tabs = e.getData(JBTabsImpl.NAVIGATION_ACTIONS_KEY);
    if (tabs == null || !tabs.isEditorTabs()) {
      return false;
    }
    return tabs.canShowMorePopup();
  }
}
