// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.actions;

import com.intellij.find.FindManager;
import com.intellij.find.FindUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.editor.actions.IncrementalFindAction.SEARCH_DISABLED;

@ApiStatus.Internal
public final class SearchBackAction extends EditorAction implements DumbAware {
  public SearchBackAction() {
    super(new Handler());
    setEnabledInModalContext(true);
  }

  private static final class Handler extends EditorActionHandler {
    @Override
    protected void doExecute(@NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
      final Project project = dataContext.getData(CommonDataKeys.PROJECT);
      if (project == null) return;
      PsiDocumentManager.getInstance(project).commitAllDocuments();
      FindManager findManager = FindManager.getInstance(project);
      if(!findManager.selectNextOccurrenceWasPerformed() && findManager.findPreviousUsageInEditor(editor)) {
        return;
      }
      FindUtil.searchBack(project, editor, dataContext);
    }

    @Override
    protected boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
      Project project = dataContext.getData(CommonDataKeys.PROJECT);
      if (project == null) {
        return false;
      }
      return !editor.isOneLineMode() && !SEARCH_DISABLED.get(editor, false);
    }
  }
}
