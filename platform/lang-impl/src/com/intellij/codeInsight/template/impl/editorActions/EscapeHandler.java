// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.template.impl.editorActions;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.lookup.LookupManager;
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
      var lookup = LookupManager.getActiveLookup(editor);

      // The idea behind lookup checking is that if there is an active lookup,
      // then user might want just to close lookup but not finish a template.
      // E.g. user wants to move to the next template segment by Tab without completion invocation.
      // If there's an active selection in the editor, then the user might want to just get rid of the selection.
      // Only if there's neither a selection nor a lookup, we can be sure that the intention is to finish the template.
      // As finishing the template is a rather destructive action, we better stay on the safe side here.
      // See KTIJ-35492 for an example of a bad UX caused by an undesired template finishing.
      if (selectionModel.hasSelection()) {
        selectionModel.removeSelection();
        // Normally, removing the selection will also hide the lookup automatically.
        // However, in some cases, especially in UI tests, it may not work,
        // most likely because of a race: it might be in an about-to-show state (calculating).
        // This breaks tests, and even in production may lead to a situation when
        // pressing Esc twice may not cancel the refactoring
        // (one removes the selection, the other cancels the lookup).
        // So we play it safe and cancel it explicitly.
        if (lookup != null) {
          lookup.hideLookup(true);
        }
      }
      else if (lookup != null && lookup.getCurrentItem() != null) {
        lookup.hideLookup(true);
      }
      else {
        CommandProcessor.getInstance().setCurrentCommandName(CodeInsightBundle.message("finish.template.command"));
        templateState.gotoEnd(true);
        return;
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
