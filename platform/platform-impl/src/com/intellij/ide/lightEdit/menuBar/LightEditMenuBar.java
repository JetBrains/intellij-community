// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit.menuBar;

import com.intellij.ide.lightEdit.actions.LightEditExitAction;
import com.intellij.ide.lightEdit.actions.LightEditNewFileAction;
import com.intellij.ide.lightEdit.actions.LightEditOpenFileInProjectAction;
import com.intellij.ide.lightEdit.actions.LightEditSaveAsAction;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.wm.impl.IdeMenuBar;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public final class LightEditMenuBar extends IdeMenuBar {
  @Override
  public @NotNull ActionGroup getMainMenuActionGroup() {
    return new DefaultActionGroup(
      createActionGroup("&File",
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
      ),
      createActionGroup("&Edit",
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
      ),
      createActionGroup("&View",
                        standardAction("EditorToggleShowWhitespaces"),
                        standardAction("EditorToggleShowLineNumbers")
      ),
      createActionGroup("&Help",
                        standardAction("HelpTopics"),
                        standardAction("About"))
    );
  }

  @NotNull
  private static ActionGroup createActionGroup(@NotNull String title, AnAction... actions) {
    return new DefaultActionGroup(title, Arrays.asList(actions));
  }

  private static AnAction standardAction(@NotNull String id) {
    return ActionManager.getInstance().getAction(id);
  }
}
