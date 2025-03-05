// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diff.impl.dir.actions;

import com.intellij.ide.diff.DirDiffModelHolder;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.impl.dir.DirDiffTableModel;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
public class DirDiffToolbarActions extends ActionGroup implements DumbAware {
  private final AnAction[] myActions;

  public DirDiffToolbarActions(DirDiffTableModel model) {
    super(DiffBundle.message("directory.diff.actions"), false);
    final List<AnAction> actions = new ArrayList<>(Arrays.asList(
      new RefreshDirDiffAction(model),
      Separator.getInstance(),
      new EnableLeft(model),
      new EnableNotEqual(model),
      new EnableEqual(model),
      new EnableRight(model),
      Separator.getInstance(),
      ActionManager.getInstance().getAction("DirDiffMenu.CompareNewFilesWithEachOtherAction"),
      Separator.getInstance(),
      new ChangeCompareModeGroup(model),
      Separator.getInstance()));

    if (model.getSettings().enableSyncActions && model.isOperationsEnabled()) {
      actions.add(new SynchronizeDiff(model, true));
      actions.add(new SynchronizeDiff(model, false));
    }

    actions.addAll(model.getSettings().getExtraActions());

    actions.add(Separator.getInstance());
    actions.add(ActionManager.getInstance().getAction(IdeActions.DIFF_VIEWER_TOOLBAR));
    myActions = actions.toArray(AnAction.EMPTY_ARRAY);
  }

  public void setUp(DirDiffTableModel model, JComponent panel) {
    for (AnAction action : myActions) {
      setUp(model, panel, action);
    }
  }

  private static void setUp(DirDiffTableModel model, JComponent panel, AnAction action) {
    if (action instanceof ShortcutProvider) {
      final ShortcutSet shortcut = ((ShortcutProvider)action).getShortcut();
      if (shortcut != null) {
        action.registerCustomShortcutSet(shortcut, panel);
      }
    }
    if (action instanceof DirDiffModelHolder) {
      ((DirDiffModelHolder)action).setModel(model);
    }
    if (action instanceof ActionGroup actionGroup) {
      AnAction[] actionChildren = actionGroup instanceof DefaultActionGroup defaultActionGroup
                                  ? defaultActionGroup.getChildren(ActionManager.getInstance())
                                  : actionGroup.getChildren(null);
      for (AnAction child : actionChildren) {
        setUp(model, panel, child);
      }
    }
  }

  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
    return myActions;
  }

  public AnAction @NotNull [] getActions() {
    return myActions;
  }
}
