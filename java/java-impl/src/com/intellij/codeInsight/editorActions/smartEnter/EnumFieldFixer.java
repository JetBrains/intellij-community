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
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.CharArrayUtil;

public class EnumFieldFixer implements Fixer {
  @Override
  public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (psiElement instanceof PsiEnumConstant) {
      int insertionOffset = psiElement.getTextRange().getEndOffset();
      Document doc = editor.getDocument();
      final CharSequence text = doc.getCharsSequence();
      final int probableCommaOffset = CharArrayUtil.shiftForward(text, insertionOffset, " \t");
      if (probableCommaOffset >= text.length() || text.charAt(probableCommaOffset) != ',') {
        doc.insertString(insertionOffset, ",");
      }
    }
  }
}
