// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit.menuBar;

import com.intellij.ide.lightEdit.actions.LightEditExitAction;
import com.intellij.ide.lightEdit.actions.LightEditNewFileAction;
import com.intellij.ide.lightEdit.actions.LightEditSaveAsAction;
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
    add(createActionMenu("&File",
                         new LightEditNewFileAction(),
                         Separator.create(),
                         standardAction("OpenFile"),
                         Separator.create(),
                         new LightEditSaveAsAction(),
                         standardAction("SaveAll"),
                         Separator.create(),
                         new LightEditExitAction()
    ));
    add(createActionMenu("&Edit",
                         standardAction(IdeActions.ACTION_UNDO),
                         standardAction(IdeActions.ACTION_REDO),
                         Separator.create(),
                         standardAction(IdeActions.ACTION_CUT),
                         standardAction(IdeActions.ACTION_COPY),
                         standardAction(IdeActions.ACTION_PASTE),
                         standardAction(IdeActions.ACTION_DELETE),
                         Separator.create(),
                         standardAction("EditorSelectWord"),
                         standardAction("EditorUnSelectWord"),
                         standardAction(IdeActions.ACTION_SELECT_ALL)
    ));
    add(createActionMenu("&View",
                         standardAction("EditorToggleShowWhitespaces"),
                         standardAction("EditorToggleShowLineNumbers")
    ));
    add(createActionMenu("&Help",
                         standardAction("HelpTopics"),
                         standardAction("About")));
  }

  @NotNull
  protected ActionMenu createActionMenu(@NotNull String title, AnAction... actions) {
    ActionGroup actionGroup = new DefaultActionGroup(title, Arrays.asList(actions));
    return new ActionMenu(null,
                          ActionPlaces.MAIN_MENU,
                          actionGroup,
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
