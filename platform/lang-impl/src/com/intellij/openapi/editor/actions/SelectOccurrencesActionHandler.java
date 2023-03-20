// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actions;

import com.intellij.codeInsight.editorActions.SelectWordUtil;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.find.FindBundle;
import com.intellij.find.FindModel;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.impl.EditorLastActionTracker;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.ui.LightweightHint;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

abstract public class SelectOccurrencesActionHandler extends EditorActionHandler {
  private static final Key<Boolean> NOT_FOUND = Key.create("select.next.occurence.not.found");
  private static final Key<Boolean> WHOLE_WORDS = Key.create("select.next.occurence.whole.words");

  private static final Set<String> SELECT_ACTIONS = Set.of(IdeActions.ACTION_SELECT_NEXT_OCCURENCE, IdeActions.ACTION_UNSELECT_PREVIOUS_OCCURENCE, IdeActions.ACTION_FIND_NEXT,
         IdeActions.ACTION_FIND_PREVIOUS);

  protected static void setSelection(Editor editor, Caret caret, TextRange selectionRange) {
    EditorActionUtil.makePositionVisible(editor, selectionRange.getStartOffset());
    EditorActionUtil.makePositionVisible(editor, selectionRange.getEndOffset());
    caret.setSelection(selectionRange.getStartOffset(), selectionRange.getEndOffset());
  }

  protected static void showHint(final Editor editor) {
    String message = FindBundle.message("select.next.occurence.not.found.message");
    final LightweightHint hint = new LightweightHint(HintUtil.createInformationLabel(message));
    HintManagerImpl.getInstanceImpl().showEditorHint(hint,
                                                     editor,
                                                     HintManager.UNDER,
                                                     HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_SCROLLING,
                                                     0,
                                                     false);
  }

  protected static boolean getAndResetNotFoundStatus(Editor editor) {
    boolean status = editor.getUserData(NOT_FOUND) != null;
    editor.putUserData(NOT_FOUND, null);
    return status && isRepeatedActionInvocation();
  }

  protected static void setNotFoundStatus(Editor editor) {
    editor.putUserData(NOT_FOUND, Boolean.TRUE);
  }

  protected static boolean isWholeWordSearch(Editor editor) {
    if (!isRepeatedActionInvocation()) {
      editor.putUserData(WHOLE_WORDS, null);
    }
    Boolean value = editor.getUserData(WHOLE_WORDS);
    return value != null;
  }

  @Nullable
  protected static TextRange getSelectionRange(Editor editor, Caret caret) {
    return SelectWordUtil.getWordSelectionRange(editor.getDocument().getCharsSequence(),
                                                caret.getOffset(),
                                                SelectWordUtil.JAVA_IDENTIFIER_PART_CONDITION);
  }

  protected static void setWholeWordSearch(Editor editor, boolean isWholeWordSearch) {
    editor.putUserData(WHOLE_WORDS, isWholeWordSearch);
  }

  protected static boolean isRepeatedActionInvocation() {
    String lastActionId = EditorLastActionTracker.getInstance().getLastActionId();
    return lastActionId != null && SELECT_ACTIONS.contains(lastActionId);
  }

  protected static FindModel getFindModel(String text, boolean wholeWords) {
    FindModel model = new FindModel();
    model.setStringToFind(text);
    model.setCaseSensitive(true);
    model.setWholeWordsOnly(wholeWords);
    return model;
  }
}
