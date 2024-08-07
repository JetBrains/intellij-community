// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.impl.ActionMenu;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.TextWithMnemonic;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.function.Supplier;

@ApiStatus.Internal
public class WeighingNewActionGroup extends WeighingActionGroup implements DumbAware {
  private ActionGroup myDelegate;

  @Override
  public @NotNull ActionGroup getDelegate() {
    if (myDelegate == null) {
      myDelegate = (ActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_NEW);
    }
    return myDelegate;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Supplier<TextWithMnemonic> prev = e.getPresentation().getTextWithPossibleMnemonic();
    super.update(e);
    if (e.getPresentation().getTextWithPossibleMnemonic() != prev) {
      e.getPresentation().setTextWithMnemonic(prev);
    }

    if (isPopupGroup() && ActionPlaces.isPopupPlace(e.getPlace())) {
      Presentation p = e.getPresentation();
      p.setText(ActionsBundle.message("group.WeighingNewGroup.text.popup"));
      p.setPerformGroup(true);
      p.putClientProperty(ActionUtil.HIDE_DROPDOWN_ICON, true);
      p.putClientProperty(ActionUtil.SUPPRESS_SUBMENU, true);
    }

    if (ActionPlaces.isShortcutPlace(e.getPlace())) {
      e.getPresentation().setPerformGroup(false);
      e.getPresentation().setEnabledAndVisible(false);
    }
  }

  @Override
  protected boolean shouldBeChosenAnyway(@NotNull AnAction action) {
    final Class<? extends AnAction> aClass = action.getClass();
    return aClass == CreateFileAction.class || aClass == CreateDirectoryOrPackageAction.class ||
           "NewModuleInGroupAction".equals(aClass.getSimpleName());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    if (isPopupGroup()) {
      ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(
        ActionsBundle.message("NewFile.popup.title"), this, e.getDataContext(), JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
        false, null, 20, null, ActionPlaces.getPopupPlace("NewFile"));
      InputEvent inputEvent = e.getInputEvent();
      if (inputEvent instanceof MouseEvent) {
        Component component = inputEvent.getComponent();
        popup.show(new RelativePoint(component, new Point(0, 0)));
      }
      else {
        popup.showInBestPositionFor(e.getDataContext());
      }
    }
  }

  private static boolean isPopupGroup() {
    return Registry.is("ide.project.view.new.file.popup");
  }
}
