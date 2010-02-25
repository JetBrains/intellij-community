/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.execution.ui.layout.actions;

import com.intellij.execution.ui.actions.BaseViewAction;
import com.intellij.execution.ui.layout.Grid;
import com.intellij.execution.ui.layout.Tab;
import com.intellij.execution.ui.layout.ViewContext;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.ui.content.Content;

public class MoveToTabAction extends BaseViewAction {
  protected void update(final AnActionEvent e, final ViewContext context, final Content[] content) {
    if (!context.isMoveToGridActionEnabled() || content.length != 1) {
      setEnabled(e, false);
      return;
    }
    if (isDetached(context, content[0])) {
      setEnabled(e, false);
      return;
    }

    Grid grid = context.findGridFor(content[0]);
    if (grid == null) {
      setEnabled(e, false);
      return;
    }


    Tab tab = context.getTabFor(grid);

    if (ViewContext.TAB_TOOLBAR_PLACE.equals(e.getPlace())) {
      setEnabled(e, false);
    } else {
      setEnabled(e, tab.isDefault());
    }
  }

  protected void actionPerformed(final AnActionEvent e, final ViewContext context, final Content[] content) {
    context.getCellTransform().moveToTab(content[0]);
  }
}