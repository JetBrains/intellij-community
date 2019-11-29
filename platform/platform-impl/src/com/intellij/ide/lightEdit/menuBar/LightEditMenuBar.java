// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit.menuBar;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionMenu;
import com.intellij.openapi.actionSystem.impl.MenuItemPresentationFactory;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.mac.foundation.NSDefaults;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Arrays;

public class LightEditMenuBar extends JMenuBar {

  private final PresentationFactory myPresentationFactory;

  public LightEditMenuBar() {
    myPresentationFactory = new MenuItemPresentationFactory();
    ActionGroup fileActions = new DefaultActionGroup(
      "&File", Arrays.asList(standardAction("OpenFile"),
                             standardAction("SaveAll")
    ));
    ActionMenu actionMenu = createActionMenu(fileActions);
    add(actionMenu);
  }

  @NotNull
  protected ActionMenu createActionMenu(ActionGroup action) {
    return new ActionMenu(null,
                          ActionPlaces.MAIN_MENU,
                          action,
                          myPresentationFactory,
                          !UISettings.getInstance().getDisableMnemonics(),
                          isDarkMacMenu());
  }

  private static boolean isDarkMacMenu() {
    return SystemInfo.isMacSystemMenu && NSDefaults.isDarkMenuBar();
  }

  private static AnAction standardAction(@NotNull String id) {
    return ActionManager.getInstance().getAction(id);
  }
}
