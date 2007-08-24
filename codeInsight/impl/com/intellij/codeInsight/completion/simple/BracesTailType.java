/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInsight.completion.simple;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.editorActions.EnterHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.ide.DataManager;

/**
 * @author peter
 */
public abstract class BracesTailType extends TailType {

  protected abstract boolean isSpaceBeforeLBrace(CodeStyleSettings styleSettings, Editor editor, final int tailOffset);

  public int processTail(final Editor editor, int tailOffset) {
    CodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(editor.getProject());
    if (isSpaceBeforeLBrace(styleSettings, editor, tailOffset)) {
      tailOffset = insertChar(editor, tailOffset, ' ');
    }
    tailOffset = insertChar(editor, tailOffset, '{');
    if (EnterHandler.isAfterUnmatchedLBrace(editor, tailOffset, StdFileTypes.JAVA)) {
      new EnterHandler(EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_ENTER)).executeWriteAction(editor,
                                                                                                                            DataManager.getInstance().getDataContext(editor.getContentComponent()));
      return editor.getCaretModel().getOffset();
    }
    return tailOffset;
  }

}