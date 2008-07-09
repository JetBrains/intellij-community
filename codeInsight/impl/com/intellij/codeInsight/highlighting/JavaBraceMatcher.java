package com.intellij.codeInsight.highlighting;

import com.intellij.codeInsight.hint.DeclarationRangeUtil;
import com.intellij.lang.BracePair;
import com.intellij.lang.PairedBraceMatcher;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.java.IJavaElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaBraceMatcher implements PairedBraceMatcher {
  private BracePair[] pairs = new BracePair[] {
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
    return TokenTypeEx.WHITE_SPACE_OR_COMMENT_BIT_SET.contains(tokenType)
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