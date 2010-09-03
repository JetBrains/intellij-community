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

package com.intellij.codeInsight.editorActions.enter;

import com.intellij.lang.*;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.text.CharArrayUtil;

public class EnterInLineCommentHandler implements EnterHandlerDelegate {
  public Result preprocessEnter(final PsiFile file, final Editor editor, final Ref<Integer> caretOffsetRef, final Ref<Integer> caretAdvance,
                                final DataContext dataContext, final EditorActionHandler originalHandler) {
    int caretOffset = caretOffsetRef.get().intValue();
    PsiElement psiAtOffset = file.findElementAt(caretOffset);
    if (psiAtOffset != null && psiAtOffset.getTextOffset() < caretOffset) {
      ASTNode token = psiAtOffset.getNode();
      Document document = editor.getDocument();
      CharSequence text = document.getText();
      final Language language = psiAtOffset.getLanguage();
      final Commenter languageCommenter = LanguageCommenters.INSTANCE.forLanguage(language);
      final CodeDocumentationAwareCommenter commenter = languageCommenter instanceof CodeDocumentationAwareCommenter
                                                        ? (CodeDocumentationAwareCommenter)languageCommenter:null;
      if (commenter != null && token.getElementType() == commenter.getLineCommentTokenType() ) {
        final int offset = CharArrayUtil.shiftForward(text, caretOffset, " \t");

        if (offset < document.getTextLength() && text.charAt(offset) != '\n') {
          String prefix = commenter.getLineCommentPrefix();
          assert prefix != null: "Line Comment type is set but Line Comment Prefix is null!";
          if (text.charAt(caretOffset) != ' ') {
            prefix += " ";
          }
          document.insertString(caretOffset, prefix);
          return Result.Default;
        }
      }
    }
    return Result.Continue;
  }
}
