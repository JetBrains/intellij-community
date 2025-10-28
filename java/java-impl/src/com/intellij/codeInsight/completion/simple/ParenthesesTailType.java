// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.completion.simple;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.ModNavigatorTailType;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ModNavigator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;

public abstract class ParenthesesTailType extends ModNavigatorTailType {

  protected boolean isSpaceBeforeParentheses(CommonCodeStyleSettings styleSettings, final int tailOffset) {
    return false;
  }

  protected boolean isSpaceWithinParentheses(CommonCodeStyleSettings styleSettings, final int tailOffset) {
    return false;
  }

  @Override
  public int processTail(@NotNull Project project, @NotNull ModNavigator editor, int tailOffset) {
    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    assert psiFile != null;
    Language language = PsiUtilCore.getLanguageAtOffset(psiFile, tailOffset);
    CommonCodeStyleSettings styleSettings = CodeStyle.getLanguageSettings(psiFile, language);
    if (isSpaceBeforeParentheses(styleSettings, tailOffset)) {
      tailOffset = insertChar(editor, tailOffset, ' ');
    }
    Document document = editor.getDocument();
    if (tailOffset < document.getTextLength() && document.getCharsSequence().charAt(tailOffset) == '(') {
      return moveCaret(editor, tailOffset, 1);
    }

    tailOffset = insertChar(editor, tailOffset, '(');
    if (isSpaceWithinParentheses(styleSettings, tailOffset)) {
      tailOffset = insertChar(editor, tailOffset, ' ');
      tailOffset = insertChar(editor, tailOffset, ' ');
      tailOffset = insertChar(editor, tailOffset, ')');
      moveCaret(editor, tailOffset, -2);
    } else {
      tailOffset = insertChar(editor, tailOffset, ')');
      moveCaret(editor, tailOffset, -1);
      editor.registerTabOut(TextRange.from(editor.getCaretOffset(), 0), editor.getCaretOffset() + 1);
    }
    return tailOffset;
  }

}
