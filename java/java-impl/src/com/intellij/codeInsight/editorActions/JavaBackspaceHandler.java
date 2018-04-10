/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

public class JavaBackspaceHandler extends BackspaceHandlerDelegate {
  private boolean myToDeleteGt;

  @Override
  public void beforeCharDeleted(char c, @NotNull PsiFile file, @NotNull Editor editor) {
    myToDeleteGt = c == '<' &&
                   file instanceof PsiJavaFile &&
                   PsiUtil.isLanguageLevel5OrHigher(file) &&
                   TypedHandlerUtil.isAfterClassLikeIdentifierOrDot(editor.getCaretModel().getOffset() - 1,
                                                                    editor, JavaTokenType.DOT, JavaTokenType.IDENTIFIER, true);
  }

  @Override
  public boolean charDeleted(final char c, @NotNull final PsiFile file, @NotNull final Editor editor) {
    if (c == '<' && myToDeleteGt) {
      int offset = editor.getCaretModel().getOffset();
      final CharSequence chars = editor.getDocument().getCharsSequence();
      if (editor.getDocument().getTextLength() <= offset) return false; //virtual space after end of file

      char c1 = chars.charAt(offset);
      if (c1 != '>') return true;
      TypedHandlerUtil.handleGenericLTDeletion(editor, offset, JavaTokenType.LT, JavaTokenType.GT, JavaTypedHandler.INVALID_INSIDE_REFERENCE);
      return true;
    }
    return false;
  }

  /**
   * needed for API compatibility only
   * @deprecated Please use {@link TypedHandlerUtil#handleGenericGT} instead
   */
  @Deprecated
  public static void handleLTDeletion(@NotNull final Editor editor,
                                      final int offset,
                                      @NotNull final IElementType lt,
                                      @NotNull final IElementType gt,
                                      @NotNull final TokenSet invalidInsideReference) {
    TypedHandlerUtil.handleGenericLTDeletion(editor, offset, lt, gt, invalidInsideReference);
  }
}
