/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.editor.actions;

import com.intellij.codeInsight.editorActions.SelectWordUtil;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.find.FindBundle;
import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.find.FindResult;
import com.intellij.openapi.editor.EditorLastActionTracker;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.ui.LightweightHint;
import org.jetbrains.annotations.Nullable;

public class SelectNextOccurrenceAction extends EditorAction {
  private static final Key<Boolean> NOT_FOUND = Key.create("select.next.occurence.not.found");
  private static final Key<Boolean> WHOLE_WORDS = Key.create("select.next.occurence.whole.words");

  protected SelectNextOccurrenceAction() {
    super(new Handler());
  }

  static class Handler extends EditorActionHandler {
    @Override
    public boolean isEnabled(Editor editor, DataContext dataContext) {
      return super.isEnabled(editor, dataContext) && editor.getProject() != null && editor.getCaretModel().supportsMultipleCarets();
    }

    @Override
    public void execute(Editor editor, @Nullable Caret c, DataContext dataContext) {
      Caret caret = c == null ? editor.getCaretModel().getPrimaryCaret() : c;
      TextRange wordSelectionRange = SelectWordUtil.getWordSelectionRange(editor.getDocument().getCharsSequence(),
                                                                          caret.getOffset(),
                                                                          SelectWordUtil.JAVA_IDENTIFIER_PART_CONDITION);
      boolean notFoundPreviously = getAndResetNotFoundStatus(editor);
      boolean wholeWordSearch = isWholeWordSearch(editor);
      if (caret.hasSelection()) {
        Project project = editor.getProject();
        String selectedText = caret.getSelectedText();
        if (project == null || selectedText == null) {
          return;
        }
        FindManager findManager = FindManager.getInstance(project);

        FindModel model = new FindModel();
        model.setStringToFind(caret.getSelectedText());
        model.setCaseSensitive(true);
        model.setWholeWordsOnly(wholeWordSearch);

        int searchStartOffset = notFoundPreviously ? 0 : caret.getSelectionEnd();
        FindResult findResult = findManager.findString(editor.getDocument().getCharsSequence(), searchStartOffset, model);
        if (findResult.isStringFound()) {
          int newCaretOffset = caret.getOffset() - caret.getSelectionStart() + findResult.getStartOffset();
          EditorActionUtil.makePositionVisible(editor, newCaretOffset);
          Caret newCaret = editor.getCaretModel().addCaret(editor.offsetToVisualPosition(newCaretOffset));
          if (newCaret == null) {
            // this means that the found occurence is already selected
            if (notFoundPreviously) {
              setNotFoundStatus(editor); // to make sure we won't show hint anymore if there are no more occurrences
            }
          }
          else {
            setSelection(editor, newCaret, findResult);
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

    private static void setSelection(Editor editor, Caret caret, TextRange selectionRange) {
      EditorActionUtil.makePositionVisible(editor, selectionRange.getStartOffset());
      EditorActionUtil.makePositionVisible(editor, selectionRange.getEndOffset());
      caret.setSelection(selectionRange.getStartOffset(), selectionRange.getEndOffset());
    }


    private static void showHint(final Editor editor) {
      String message = FindBundle.message("select.next.occurence.not.found.message");
      final LightweightHint hint = new LightweightHint(HintUtil.createInformationLabel(message));
      HintManagerImpl.getInstanceImpl().showEditorHint(hint,
                                                       editor,
                                                       HintManager.UNDER,
                                                       HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_SCROLLING,
                                                       0,
                                                       false);
    }

    static boolean getAndResetNotFoundStatus(Editor editor) {
      boolean status = editor.getUserData(NOT_FOUND) != null;
      editor.putUserData(NOT_FOUND, null);
      return status && isRepeatedActionInvocation();
    }

    private static void setNotFoundStatus(Editor editor) {
      editor.putUserData(NOT_FOUND, Boolean.TRUE);
    }

    private static boolean isWholeWordSearch(Editor editor) {
      if (!isRepeatedActionInvocation()) {
        editor.putUserData(WHOLE_WORDS, null);
      }
      Boolean value = editor.getUserData(WHOLE_WORDS);
      return value != null;
    }

    private static void setWholeWordSearch(Editor editor, boolean isWholeWordSearch) {
      editor.putUserData(WHOLE_WORDS, isWholeWordSearch);
    }

    private static boolean isRepeatedActionInvocation() {
      String lastActionId = EditorLastActionTracker.getInstance().getLastActionId();
      return IdeActions.ACTION_SELECT_NEXT_OCCURENCE.equals(lastActionId) || IdeActions.ACTION_UNSELECT_LAST_OCCURENCE.equals(lastActionId);
    }
  }
}
