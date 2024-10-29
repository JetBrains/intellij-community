// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actions;

import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.find.FindResult;
import com.intellij.find.FindUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.editor.actions.IncrementalFindAction.SEARCH_DISABLED;

@ApiStatus.Internal
public final class SelectNextOccurrenceAction extends EditorAction implements ActionRemoteBehaviorSpecification.Frontend {
  private SelectNextOccurrenceAction() {
    super(new Handler());
  }

  static final class Handler extends SelectOccurrencesActionHandler {
    @Override
    public boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
      return editor.getProject() != null && editor.getCaretModel().supportsMultipleCarets()
        && !SEARCH_DISABLED.get(editor, false);
    }

    @Override
    public void doExecute(@NotNull Editor editor, @Nullable Caret c, DataContext dataContext) {
      Caret caret = c == null ? editor.getCaretModel().getPrimaryCaret() : c;
      TextRange wordSelectionRange = getSelectionRange(editor, caret);
      boolean notFoundPreviously = getAndResetNotFoundStatus(editor);
      boolean wholeWordSearch = isWholeWordSearch(editor);
      if (caret.hasSelection()) {
        Project project = editor.getProject();
        String selectedText = caret.getSelectedText();
        if (project == null || selectedText == null) {
          return;
        }
        FindManager findManager = FindManager.getInstance(project);

        FindModel model = getFindModel(selectedText, wholeWordSearch);

        findManager.setSelectNextOccurrenceWasPerformed();
        findManager.setFindNextModel(model);

        int searchStartOffset = notFoundPreviously ? 0 : caret.getSelectionEnd();
        FindResult findResult = findManager.findString(editor.getDocument().getCharsSequence(), searchStartOffset, model);
        if (findResult.isStringFound()) {
          boolean caretAdded = FindUtil.selectSearchResultInEditor(editor, findResult, caret.getOffset() - caret.getSelectionStart());
          if (!caretAdded) {
            // this means that the found occurence is already selected
            if (notFoundPreviously) {
              setNotFoundStatus(editor); // to make sure we won't show hint anymore if there are no more occurrences
            }
          }
        }
        else {
          setNotFoundStatus(editor);
          showHint(editor);
        }
      }
      else {
        if (wordSelectionRange == null) {
          return;
        }
        setSelection(editor, caret, wordSelectionRange);
        setWholeWordSearch(editor, true);
      }
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    }
  }
}
