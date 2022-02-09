// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.editorHeaderActions;

import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.find.FindBundle;
import com.intellij.find.FindModel;
import com.intellij.find.SearchSession;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ShowFilterPopupGroup extends DefaultActionGroup implements ShortcutProvider {
  public ShowFilterPopupGroup() {
    super(new ToggleAnywhereAction(),
          new ToggleInCommentsAction(),
          new ToggleInLiteralsOnlyAction(),
          new ToggleExceptCommentsAction(),
          new ToggleExceptLiteralsAction(),
          new ToggleExceptCommentsAndLiteralsAction());
    setPopup(true);
    getTemplatePresentation().setText(FindBundle.message("find.popup.show.filter.popup"));
    getTemplatePresentation().setIcon(AllIcons.General.Filter);
    getTemplatePresentation().putClientProperty(ActionButton.HIDE_DROPDOWN_ICON, Boolean.TRUE);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    SearchSession session = e.getData(SearchSession.KEY);
    if (session == null) {
      e.getPresentation().setEnabled(false);
      return;
    }
    Icon icon = getTemplatePresentation().getIcon();
    if (icon != null && enableLiveIndicator(session.getFindModel())) {
      e.getPresentation().setIcon(ExecutionUtil.getLiveIndicator(icon));
    }
    else {
      e.getPresentation().setIcon(icon);
    }
  }

  protected boolean enableLiveIndicator(@NotNull FindModel model) {
    return model.getSearchContext() != FindModel.SearchContext.ANY;
  }

  @Override
  public @Nullable ShortcutSet getShortcut() {
    KeyboardShortcut keyboardShortcut = ActionManager.getInstance().getKeyboardShortcut("ShowFilterPopup");
    if (keyboardShortcut != null)
      return new CustomShortcutSet(keyboardShortcut);
    return null;
  }
}
