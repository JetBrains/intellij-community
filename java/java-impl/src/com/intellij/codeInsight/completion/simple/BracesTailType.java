// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.completion.simple;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.editorActions.EnterHandler;
import com.intellij.codeInsight.editorActions.enter.EnterAfterUnmatchedBraceHandler;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

public class BracesTailType extends TailType {

  @Override
  public int processTail(final @NotNull Editor editor, int tailOffset) {
    int startOffset = tailOffset;

    CharSequence seq = editor.getDocument().getCharsSequence();
    int nextNonWs = CharArrayUtil.shiftForward(seq, tailOffset, " \t");
    if (nextNonWs < seq.length() && seq.charAt(nextNonWs) == '{') {
      tailOffset = nextNonWs + 1;
    } else {
      tailOffset = insertChar(editor, startOffset, '{');
    }

    tailOffset = reformatBrace(editor, tailOffset, startOffset);

    if (EnterAfterUnmatchedBraceHandler.isAfterUnmatchedLBrace(editor, tailOffset, getFileType(editor))) {
      new EnterHandler(EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_ENTER))
        .executeWriteAction(editor, editor.getCaretModel().getCurrentCaret(),
                            DataManager.getInstance().getDataContext(editor.getContentComponent()));
      return editor.getCaretModel().getOffset();
    }
    return tailOffset;
  }

  private static int reformatBrace(Editor editor, int tailOffset, int startOffset) {
    Project project = editor.getProject();
    if (project != null) {
      PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      if (psiFile != null) {
        editor.getCaretModel().moveToOffset(tailOffset);
        CodeStyleManager.getInstance(project).reformatText(psiFile, startOffset, tailOffset);
        tailOffset = editor.getCaretModel().getOffset();
      }
    }
    return tailOffset;
  }
}