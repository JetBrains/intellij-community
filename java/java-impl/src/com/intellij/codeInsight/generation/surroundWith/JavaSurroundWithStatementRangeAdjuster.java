/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;

public class JavaSurroundWithStatementRangeAdjuster implements SurroundWithRangeAdjuster {

  @Nullable
  @Override
  public TextRange adjustSurroundWithRange(PsiFile file, TextRange selectedRange) {
    return selectedRange;
  }

  @Nullable
  @Override
  public TextRange adjustSurroundWithRange(PsiFile file, TextRange selectedRange, boolean hasSelection) {
    if (!hasSelection) {
      int startOffset = selectedRange.getStartOffset();
      int endOffset = selectedRange.getEndOffset();
      if (CodeInsightUtil.findStatementsInRange(file, startOffset, endOffset).length == 0) {
        PsiElement elementAtLineStart = findNonWhiteSpaceElement(file, startOffset);
        PsiElement statement = PsiTreeUtil.getParentOfType(elementAtLineStart, PsiStatement.class, false);
        if (statement != null && statement.getTextRange().getStartOffset() == elementAtLineStart.getTextRange().getStartOffset()) {
          return statement.getTextRange();
        }
      }
    }
    return selectedRange;
  }

  private static PsiElement findNonWhiteSpaceElement(PsiFile file, int startOffset) {
    PsiElement leaf = file.findElementAt(startOffset);
    return leaf instanceof PsiWhiteSpace ? PsiTreeUtil.skipWhitespacesForward(leaf) : leaf;
  }
}
