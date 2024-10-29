// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.template.impl.editorActions;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public class EscapeHandler extends EditorActionHandler {
  private final EditorActionHandler myOriginalHandler;

  public EscapeHandler(EditorActionHandler originalHandler) {
    myOriginalHandler = originalHandler;
  }

  @Override
  public void doExecute(@NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
    TemplateState templateState = TemplateManagerImpl.getTemplateState(editor);
    if (templateState != null && !templateState.isFinished()) {
      SelectionModel selectionModel = editor.getSelectionModel();
      LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(editor);

      // the idea behind lookup checking is that if there is a preselected value in lookup
      // then user might want just to close lookup but not finish a template.
      // E.g. user wants to move to the next template segment by Tab without completion invocation.
      // If there is no selected value in completion that user definitely wants to finish template
      boolean lookupIsEmpty = lookup == null || lookup.getCurrentItem() == null;
      if (!selectionModel.hasSelection() && lookupIsEmpty) {
        CommandProcessor.getInstance().setCurrentCommandName(CodeInsightBundle.message("finish.template.command"));
        templateState.gotoEnd(true);
        return;
      }
      else if (lookup != null) { //to hide lookup and remove selection at once
        lookup.hide();
      }
    }

    if (myOriginalHandler.isEnabled(editor, caret, dataContext)) {
      myOriginalHandler.execute(editor, caret, dataContext);
    }
  }

  @Override
  public boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
    final TemplateState templateState = TemplateManagerImpl.getTemplateState(editor);
    if (templateState != null && !templateState.isFinished()) {
      return true;
    }
    return myOriginalHandler.isEnabled(editor, caret, dataContext);
  }
}
