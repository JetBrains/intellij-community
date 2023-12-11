// Copyright 2000-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.definition.AbstractBasicJavaDefinitionService;
import com.intellij.openapi.editor.Editor;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.AbstractBasicJavaFile;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JavaBackspaceHandler extends BackspaceHandlerDelegate {
  private boolean myToDeleteGt;

  @Override
  public void beforeCharDeleted(char c, @NotNull PsiFile file, @NotNull Editor editor) {
    myToDeleteGt = c == '<' &&
                   isHigherThan50r(file) &&
                   TypedHandlerUtil.isAfterClassLikeIdentifierOrDot(editor.getCaretModel().getOffset() - 1,
                                                                    editor, JavaTokenType.DOT, JavaTokenType.IDENTIFIER, true);
  }

  private static boolean isHigherThan50r(@Nullable PsiFile file){
      return file instanceof AbstractBasicJavaFile &&
             AbstractBasicJavaDefinitionService.getJavaDefinitionService().getLanguageLevel(file).isAtLeast(LanguageLevel.JDK_1_5);
  }

  @Override
  public boolean charDeleted(final char c, @NotNull final PsiFile file, @NotNull final Editor editor) {
    if (c == '<' && myToDeleteGt) {
      int offset = editor.getCaretModel().getOffset();
      final CharSequence chars = editor.getDocument().getCharsSequence();
      if (editor.getDocument().getTextLength() <= offset) return false; //virtual space after end of file

      char c1 = chars.charAt(offset);
      if (c1 != '>') return true;
      TypedHandlerUtil.handleGenericLTDeletion(editor, offset, JavaTokenType.LT, JavaTokenType.GT, JavaTypingTokenSets.INVALID_INSIDE_REFERENCE);
      return true;
    }
    return false;
  }
}
