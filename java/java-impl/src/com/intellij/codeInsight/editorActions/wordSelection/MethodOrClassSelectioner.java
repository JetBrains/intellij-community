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
package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;

import java.util.List;

/**
   *
   */

public class MethodOrClassSelectioner extends BasicSelectioner {
  public boolean canSelect(PsiElement e) {
    return e instanceof PsiClass && !(e instanceof PsiTypeParameter) || e instanceof PsiMethod;
  }

  public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
    List<TextRange> result = super.select(e, editorText, cursorOffset, editor);

    PsiElement firstChild = e.getFirstChild();
    PsiElement[] children = e.getChildren();

    if (firstChild instanceof PsiDocComment) {
      int i = 1;

      while (children[i] instanceof PsiWhiteSpace) {
        i++;
      }

      TextRange range = new TextRange(children[i].getTextRange().getStartOffset(), e.getTextRange().getEndOffset());
      result.addAll(expandToWholeLine(editorText, range));

      range = TextRange.create(firstChild.getTextRange());
      result.addAll(expandToWholeLine(editorText, range));
    }
    else if (firstChild instanceof PsiComment) {
      int i = 1;

      while (children[i] instanceof PsiComment || children[i] instanceof PsiWhiteSpace) {
        i++;
      }
      PsiElement last = children[i - 1] instanceof PsiWhiteSpace ? children[i - 2] : children[i - 1];
      TextRange range = new TextRange(firstChild.getTextRange().getStartOffset(), last.getTextRange().getEndOffset());
      if (range.contains(cursorOffset)) {
        result.addAll(expandToWholeLine(editorText, range));
      }

      range = new TextRange(children[i].getTextRange().getStartOffset(), e.getTextRange().getEndOffset());
      result.addAll(expandToWholeLine(editorText, range));
    }

    if (e instanceof PsiClass) {
      int start = CodeBlockOrInitializerSelectioner.findOpeningBrace(children);
      // in non-Java PsiClasses, there can be no opening brace
      if (start != 0) {
        int end = CodeBlockOrInitializerSelectioner.findClosingBrace(children, start);

        result.addAll(expandToWholeLine(editorText, new TextRange(start, end)));
      }
    }


    return result;
  }
}
