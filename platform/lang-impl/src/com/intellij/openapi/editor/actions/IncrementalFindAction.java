// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.editor.actions;

import com.intellij.execution.impl.ConsoleViewUtil;
import com.intellij.find.EditorSearchSession;
import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.find.FindUtil;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IncrementalFindAction extends EditorAction implements ActionRemoteBehaviorSpecification.Frontend {
  public static final Key<Boolean> SEARCH_DISABLED = Key.create("EDITOR_SEARCH_DISABLED");

  public static class Handler extends EditorActionHandler {

    private final boolean myReplace;

    public Handler(boolean isReplace) {

      myReplace = isReplace;
    }

    @Override
    protected void doExecute(@NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
      Project project = CommonDataKeys.PROJECT.getData(dataContext);
      if (!editor.isOneLineMode() && project != null) {
        EditorSearchSession search = EditorSearchSession.get(editor);
        if (search != null) {
          search.getComponent().requestFocusInTheSearchFieldAndSelectContent(project);
          FindUtil.configureFindModel(myReplace, editor, search.getFindModel(), false);
        }
        else {
          FindManager findManager = FindManager.getInstance(project);
          FindModel model;
          if (myReplace) {
            model = findManager.createReplaceInFileModel();
          }
          else {
            model = new FindModel();
            model.copyFrom(findManager.getFindInFileModel());
          }
          boolean consoleViewEditor = ConsoleViewUtil.isConsoleViewEditor(editor);
          FindUtil.configureFindModel(myReplace, editor, model, consoleViewEditor);
          EditorSearchSession.start(editor, model, project).getComponent()
            .requestFocusInTheSearchFieldAndSelectContent(project);
          if (!consoleViewEditor && editor.getSelectionModel().hasSelection()) {
            // selection is used as string to find without search model modification so save the pattern explicitly
            FindUtil.updateFindInFileModel(project, model, true);
            FindUtil.updateFindNextModel(project, model);
          }
        }
      }
    }

    @Override
    protected boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
      if (myReplace && ConsoleViewUtil.isConsoleViewEditor(editor) &&
          !ConsoleViewUtil.isReplaceActionEnabledForConsoleViewEditor(editor)) {
        return false;
      }
      if (SEARCH_DISABLED.get(editor, false)) {
        return false;
      }
      Project project = CommonDataKeys.PROJECT.getData(dataContext);
      return project != null && !editor.isOneLineMode();
    }
  }

  public IncrementalFindAction() {
    super(new Handler(false));
  }
}