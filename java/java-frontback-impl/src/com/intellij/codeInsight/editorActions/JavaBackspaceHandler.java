// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.definition.AbstractBasicJavaDefinitionService;
import com.intellij.openapi.editor.Editor;
import com.intellij.pom.java.JavaFeature;
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
                   areGenericsAvailable(file) &&
                   TypedHandlerUtil.isAfterClassLikeIdentifierOrDot(editor.getCaretModel().getOffset() - 1,
                                                                    editor, JavaTokenType.DOT, JavaTokenType.IDENTIFIER, true);
  }

  private static boolean areGenericsAvailable(@Nullable PsiFile file){
      return file instanceof AbstractBasicJavaFile &&
             JavaFeature.GENERICS.isSufficient(AbstractBasicJavaDefinitionService.getJavaDefinitionService().getLanguageLevel(file));
  }

  @Override
  public boolean charDeleted(final char c, final @NotNull PsiFile file, final @NotNull Editor editor) {
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
