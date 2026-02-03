// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.editor.actions;

import com.intellij.find.FindUtil;
import com.intellij.find.impl.livePreview.SearchResults;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.editor.actions.IncrementalFindAction.SEARCH_DISABLED;

@ApiStatus.Internal
public class FindWordAtCaretAction extends EditorAction implements ActionRemoteBehaviorSpecification.Frontend {
  protected static final class Handler extends EditorActionHandler {
    private final SearchResults.Direction myDirection;

    Handler(@NotNull SearchResults.Direction direction) {
      super();
      myDirection = direction;
    }

    @Override
    public void doExecute(@NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
      Project project = CommonDataKeys.PROJECT.getData(dataContext);
      FindUtil.findWordAtCaret(project, editor, myDirection);
    }

    @Override
    public boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
      Project project = CommonDataKeys.PROJECT.getData(dataContext);
      return project != null && !SEARCH_DISABLED.get(editor, false);
    }
  }

  public FindWordAtCaretAction() {
    this(new Handler(SearchResults.Direction.DOWN));
  }

  protected FindWordAtCaretAction(Handler defaultHandler) {
    super(defaultHandler);
  }
}
