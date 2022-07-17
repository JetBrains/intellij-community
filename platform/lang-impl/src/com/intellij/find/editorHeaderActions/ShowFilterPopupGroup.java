// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.editorHeaderActions;

import com.intellij.find.FindBundle;
import com.intellij.find.FindModel;
import com.intellij.find.SearchSession;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.ui.BadgeIconSupplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ShowFilterPopupGroup extends DefaultActionGroup implements ShortcutProvider {
  private static final BadgeIconSupplier FILTER_ICON = new BadgeIconSupplier(AllIcons.General.Filter);

  public ShowFilterPopupGroup() {
    super(new ToggleAnywhereAction(),
          new ToggleInCommentsAction(),
          new ToggleInLiteralsOnlyAction(),
          new ToggleExceptCommentsAction(),
          new ToggleExceptLiteralsAction(),
          new ToggleExceptCommentsAndLiteralsAction());
    setPopup(true);
    getTemplatePresentation().setText(FindBundle.message("find.popup.show.filter.popup"));
    getTemplatePresentation().setIcon(FILTER_ICON.getOriginalIcon());
    getTemplatePresentation().putClientProperty(ActionButton.HIDE_DROPDOWN_ICON, Boolean.TRUE);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    SearchSession session = e.getData(SearchSession.KEY);
    if (session == null) {
      e.getPresentation().setEnabled(false);
      return;
    }
    e.getPresentation().setIcon(FILTER_ICON.getLiveIndicatorIcon(enableLiveIndicator(session.getFindModel())));
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
