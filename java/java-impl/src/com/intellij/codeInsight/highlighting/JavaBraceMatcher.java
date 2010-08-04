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
package com.intellij.codeInsight.highlighting;

import com.intellij.codeInsight.hint.DeclarationRangeUtil;
import com.intellij.lang.BracePair;
import com.intellij.lang.PairedBraceMatcher;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.java.IJavaElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaBraceMatcher implements PairedBraceMatcher {
  private final BracePair[] pairs = new BracePair[] {
      new BracePair(JavaTokenType.LPARENTH, JavaTokenType.RPARENTH, false),
      new BracePair(JavaTokenType.LBRACE, JavaTokenType.RBRACE, true),
      new BracePair(JavaTokenType.LBRACKET, JavaTokenType.RBRACKET, false),
      new BracePair(JavaDocTokenType.DOC_INLINE_TAG_START, JavaDocTokenType.DOC_INLINE_TAG_END, false),
  };

  public BracePair[] getPairs() {
    return pairs;
  }

  public boolean isPairedBracesAllowedBeforeType(@NotNull final IElementType lbraceType, @Nullable final IElementType contextType) {
    if (contextType instanceof IJavaElementType) return isPairedBracesAllowedBeforeTypeInJava(contextType);
    return true;
  }

  private static boolean isPairedBracesAllowedBeforeTypeInJava(final IElementType tokenType) {
    return ElementType.JAVA_COMMENT_OR_WHITESPACE_BIT_SET.contains(tokenType)
            || tokenType == JavaTokenType.SEMICOLON
            || tokenType == JavaTokenType.COMMA
            || tokenType == JavaTokenType.RPARENTH
            || tokenType == JavaTokenType.RBRACKET
            || tokenType == JavaTokenType.RBRACE
            || tokenType == JavaTokenType.LBRACE;
  }

  public int getCodeConstructStart(final PsiFile file, int openingBraceOffset) {
    PsiElement element = file.findElementAt(openingBraceOffset);
    if (element == null || element instanceof PsiFile) return openingBraceOffset;
    PsiElement parent = element.getParent();
    if (parent instanceof PsiCodeBlock) {
      parent = parent.getParent();
      if (parent instanceof PsiMethod || parent instanceof PsiClassInitializer) {
        TextRange range = DeclarationRangeUtil.getDeclarationRange(parent);
        return range.getStartOffset();
      }
      else if (parent instanceof PsiStatement) {
        if (parent instanceof PsiBlockStatement && parent.getParent() instanceof PsiStatement) {
          parent = parent.getParent();
        }
        return parent.getTextRange().getStartOffset();
      }
    }
    else if (parent instanceof PsiClass) {
      TextRange range = DeclarationRangeUtil.getDeclarationRange(parent);
      return range.getStartOffset();
    }
    return openingBraceOffset;
  }
}
