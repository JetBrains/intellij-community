// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.BasicJavaAstTreeUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.psi.impl.source.BasicJavaDocElementType.*;

public final class DocTagSelectioner extends WordSelectioner {
  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    ASTNode node = BasicJavaAstTreeUtil.toNode(e);
    return BasicJavaAstTreeUtil.is(node, BASIC_DOC_TAG, BASIC_DOC_SNIPPET_TAG, BASIC_DOC_INLINE_TAG);
  }

  @Override
  public List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {
    List<TextRange> result = super.select(e, editorText, cursorOffset, editor);
    if (result == null) {
      return null;
    }
    result.add(getDocTagRange(e, editorText, cursorOffset));
    return result;
  }

  public static TextRange getDocTagRange(@NotNull PsiElement e, @NotNull CharSequence documentText, int minOffset) {
    TextRange range = e.getTextRange();

    int endOffset = range.getEndOffset();
    int startOffset = range.getStartOffset();

    ASTNode node = BasicJavaAstTreeUtil.toNode(e);
    if (node == null) {
      return null;
    }

    List<ASTNode> children = BasicJavaAstTreeUtil.getChildren(node);

    for (int i = children.size() - 1; i >= 0; i--) {
      ASTNode child = children.get(i);

      int childStartOffset = child.getTextRange().getStartOffset();

      if (childStartOffset <= minOffset) {
        break;
      }

      if (BasicJavaAstTreeUtil.isDocToken(child)) {
        CharSequence chars = child.getChars();
        int shift = CharArrayUtil.shiftForward(chars, 0, " \t\n\r");

        if (shift != chars.length() && !BasicJavaAstTreeUtil.is(child, JavaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS)) {
          break;
        }
      }
      else if (!(BasicJavaAstTreeUtil.isWhiteSpace(child))) {
        break;
      }

      endOffset = Math.min(childStartOffset, endOffset);
    }

    startOffset = CharArrayUtil.shiftBackward(documentText, startOffset - 1, "* \t") + 1;

    return new TextRange(startOffset, endOffset);
  }
}
