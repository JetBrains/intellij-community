// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui.layout.actions;

import com.intellij.execution.ui.actions.BaseViewAction;
import com.intellij.execution.ui.layout.Tab;
import com.intellij.execution.ui.layout.ViewContext;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
@SuppressWarnings("ComponentNotRegistered")
public final class MinimizeViewAction extends BaseViewAction {
  @Override
  protected void update(final AnActionEvent e, final ViewContext context, final Content[] content) {
    setEnabled(e, isEnabled(context, content, e.getPlace()));
    e.getPresentation().setIcon(AllIcons.Actions.MoveToButton);
  }

  @Override
  protected void actionPerformed(final AnActionEvent e, final ViewContext context, final Content[] content) {
    for (Content each : content) {
      context.findCellFor(each).minimize(each);
    }
  }

  public static boolean isEnabled(ViewContext context, Content[] content, String place) {
    if (!context.isMinimizeActionEnabled() || content.length == 0) {
      return false;
    }

    if (ViewContext.TAB_TOOLBAR_PLACE.equals(place) || ViewContext.TAB_POPUP_PLACE.equals(place)) {
      Tab tab = getTabFor(context, content);
      if (tab == null) {
        return false;
      }
      return !tab.isDefault() && content.length == 1;
    }
    else {
      return getTabFor(context, content) != null;
    }
  }
}
