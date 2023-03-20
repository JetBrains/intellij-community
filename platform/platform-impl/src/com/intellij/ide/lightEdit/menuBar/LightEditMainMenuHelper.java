// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit.menuBar;

import com.intellij.ide.lightEdit.actions.*;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.util.NlsActions;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public final class LightEditMainMenuHelper {
  public @NotNull ActionGroup getMainMenuActionGroup() {
    DefaultActionGroup topGroup = new DefaultActionGroup();
    topGroup.add(
      createActionGroup(ActionsBundle.message("group.FileMenu.text"),
                        new LightEditOpenFileInProjectAction(),
                        Separator.create(),
                        new LightEditNewFileAction(),
                        Separator.create(),
                        standardAction("OpenFile"),
                        new LightEditRecentFileActionGroup(),
                        Separator.create(),
                        new LightEditSaveAsAction(),
                        standardAction("SaveAll"),
                        Separator.create(),
                        new LightEditReloadFileAction(),
                        Separator.create(),
                        new LightEditExitAction()
      )
    );
    topGroup.add(
      createActionGroup(ActionsBundle.message("group.EditMenu.text"),
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
      )
    );
    topGroup.add(
      createActionGroup(ActionsBundle.message("group.ViewMenu.text"),
                        standardAction(IdeActions.ACTION_EDITOR_USE_SOFT_WRAPS),
                        Separator.create(),
                        standardAction("EditorToggleShowWhitespaces"),
                        standardAction("EditorToggleShowLineNumbers")
      )
    );
    topGroup.add(
      createActionGroup(ActionsBundle.message("group.CodeMenu.text"),
                        standardAction(IdeActions.ACTION_EDITOR_REFORMAT))
    );
    topGroup.add(
      createActionGroup(ActionsBundle.message("group.WindowMenu.text"),
                        standardAction("NextProjectWindow"),
                        standardAction("PreviousProjectWindow"))
    );
    topGroup.add(
      createActionGroup(ActionsBundle.message("group.HelpMenu.text"),
                        standardAction("GotoAction"),
                        Separator.create(),
                        standardAction("HelpTopics"),
                        standardAction("About"))
    );
    return topGroup;
  }

  @NotNull
  private static ActionGroup createActionGroup(@NotNull @NlsActions.ActionText String title, AnAction... actions) {
    return new DefaultActionGroup(title, Arrays.asList(actions));
  }

  private static AnAction standardAction(@NotNull String id) {
    return ActionManager.getInstance().getAction(id);
  }
}
