// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything.groups;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.runAnything.RunAnythingSearchListModel;
import com.intellij.ide.actions.runAnything.items.RunAnythingActionItem;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class RunAnythingActionGroup<T extends AnAction> extends RunAnythingGroupBase {
  @NotNull
  protected abstract String getPrefix();

  @Nullable
  protected String getActionText(@NotNull T action) {
    return null;
  }

  @NotNull
  protected abstract List<T> getActions(@Nullable Module module);

  @Override
  public SearchResult getItems(@NotNull Project project,
                               @Nullable Module module,
                               @NotNull RunAnythingSearchListModel model,
                               @NotNull String pattern,
                               boolean isInsertionMode,
                               @NotNull Runnable cancellationChecker) {

    final SearchResult result = new SearchResult();

    cancellationChecker.run();
    for (T action : getActions(module)) {
      String actionText = getActionText(action);
      RunAnythingActionItem actionItem = new RunAnythingActionItem(action, actionText == null ? ObjectUtils
        .notNull(action.getTemplatePresentation().getText(), IdeBundle.message("run.anything.acton.group.title")) : actionText);

      if (addToList(model, result, pattern, actionItem, getPrefix() + " " + actionItem.getText(), isInsertionMode)) break;
      cancellationChecker.run();
    }

    return result;
  }
}