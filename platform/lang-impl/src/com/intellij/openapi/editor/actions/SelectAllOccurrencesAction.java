// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;

import static com.intellij.openapi.editor.actions.IncrementalFindAction.SEARCH_DISABLED;

public final class SelectAllOccurrencesAction extends EditorAction implements ActionRemoteBehaviorSpecification.Frontend {
  private SelectAllOccurrencesAction() {
    super(new Handler());
  }

  private static final class Handler extends SelectOccurrencesActionHandler {
    @Override
    public boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {

      return editor.getProject() != null
             && editor.getCaretModel().supportsMultipleCarets()
             && !SEARCH_DISABLED.get(editor, false);
    }

    @Override
    public void doExecute(final @NotNull Editor editor, @Nullable Caret c, DataContext dataContext) {
      Caret caret = c == null ? editor.getCaretModel().getPrimaryCaret() : c;

      boolean wholeWordsSearch = false;
      if (!caret.hasSelection()) {
        TextRange wordSelectionRange = getSelectionRange(editor, caret);
        if (wordSelectionRange != null) {
          setSelection(editor, caret, wordSelectionRange);
          wholeWordsSearch = true;
        }
      }

      String selectedText = caret.getSelectedText();
      Project project = editor.getProject();
      if (project == null || selectedText == null) {
        return;
      }

      int caretShiftFromSelectionStart = caret.getOffset() - caret.getSelectionStart();
      final FindManager findManager = FindManager.getInstance(project);

      final FindModel model = getFindModel(selectedText, wholeWordsSearch);

      FindUtil.selectSearchResultsInEditor(editor, new Iterator<>() {
        FindResult findResult = findManager.findString(editor.getDocument().getCharsSequence(), 0, model);

        @Override
        public boolean hasNext() {
          return findResult.isStringFound();
        }

        @Override
        public FindResult next() {
          FindResult result = findResult;
          findResult = findManager.findString(editor.getDocument().getCharsSequence(), findResult.getEndOffset(), model);
          return result;
        }

        @Override
        public void remove() {
          throw new UnsupportedOperationException();
        }
      }, caretShiftFromSelectionStart);
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    }
  }
}
