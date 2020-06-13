// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit.menuBar;

import com.intellij.ide.lightEdit.actions.*;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.wm.impl.IdeMenuBar;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public final class LightEditMenuBar extends IdeMenuBar {
  @Override
  public @NotNull ActionGroup getMainMenuActionGroup() {
    DefaultActionGroup topGroup = new DefaultActionGroup();
    topGroup.add(
      createActionGroup(ActionsBundle.message("group.FileMenu.text"),
                        new LightEditOpenFileInProjectAction(),
                        Separator.create(),
                        new LightEditNewFileAction(),
                        Separator.create(),
                        standardAction("OpenFile"),
                        new RecentFileActionGroup(),
                        Separator.create(),
                        new LightEditSaveAsAction(),
                        standardAction("SaveAll"),
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
    ObjectUtils.consumeIfNotNull(createToolActionGroup(), toolGroup -> topGroup.add(toolGroup));
    topGroup.add(
      createActionGroup(ActionsBundle.message("group.ViewMenu.text"),
                        standardAction("EditorToggleShowWhitespaces"),
                        standardAction("EditorToggleShowLineNumbers")
      )
    );
    topGroup.add(
      createActionGroup(ActionsBundle.message("group.HelpMenu.text"),
                        standardAction("HelpTopics"),
                        standardAction("About"))
    );
    return topGroup;
  }

  @NotNull
  private static ActionGroup createActionGroup(@NotNull String title, AnAction... actions) {
    return new DefaultActionGroup(title, Arrays.asList(actions));
  }

  @Nullable
  private static ActionGroup createToolActionGroup() {
    if (LightEditAssociateFileTypesAction.isAvailable()) {
      return createActionGroup(ActionsBundle.message("group.ToolsMenu.text"), new LightEditAssociateFileTypesAction());
    }
    return null;
  }

  private static AnAction standardAction(@NotNull String id) {
    return ActionManager.getInstance().getAction(id);
  }
}
