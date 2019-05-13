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

import com.intellij.psi.*;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.ArrayList;

public class ReferenceSelectioner extends BasicSelectioner {
  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    return e instanceof PsiJavaCodeReferenceElement;
  }

  @Override
  public List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {

    PsiElement endElement = e;

    while (endElement instanceof PsiJavaCodeReferenceElement && endElement.getNextSibling() != null) {
      endElement = endElement.getNextSibling();
    }

    if (!(endElement instanceof PsiJavaCodeReferenceElement) &&
        !(endElement.getPrevSibling() instanceof PsiReferenceExpression && endElement instanceof PsiExpressionList)) {
      endElement = endElement.getPrevSibling();
    }

    PsiElement element = e;
    List<TextRange> result = new ArrayList<>();
    while (element instanceof PsiJavaCodeReferenceElement) {
      PsiElement firstChild = element.getFirstChild();

      PsiElement referenceName = ((PsiJavaCodeReferenceElement)element).getReferenceNameElement();
      if (referenceName != null) {
        result.addAll(expandToWholeLine(editorText, new TextRange(referenceName.getTextRange().getStartOffset(),
                                                                  endElement.getTextRange().getEndOffset())));
        if (endElement instanceof PsiJavaCodeReferenceElement) {
          final PsiElement endReferenceName = ((PsiJavaCodeReferenceElement)endElement).getReferenceNameElement();
          if (endReferenceName != null) {
            result.addAll(expandToWholeLine(editorText, new TextRange(referenceName.getTextRange().getStartOffset(),
                                                                      endReferenceName.getTextRange().getEndOffset())));
          }
        }

      }

      if (firstChild == null) break;
      element = firstChild;
    }

//      if (element instanceof PsiMethodCallExpression) {
    result.addAll(expandToWholeLine(editorText, new TextRange(element.getTextRange().getStartOffset(),
                                                              endElement.getTextRange().getEndOffset())));
//      }

    if (!(e.getParent() instanceof PsiJavaCodeReferenceElement)) {
      if (e.getNextSibling() instanceof PsiJavaToken ||
          e.getNextSibling() instanceof PsiWhiteSpace ||
          e.getNextSibling() instanceof PsiExpressionList) {
        result.addAll(super.select(e, editorText, cursorOffset, editor));
      }
    }

    return result;
  }
}
