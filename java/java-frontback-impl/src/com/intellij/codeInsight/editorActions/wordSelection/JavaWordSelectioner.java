// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.BasicJavaAstTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.List;

public final class JavaWordSelectioner extends AbstractWordSelectioner {
  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    ASTNode node = BasicJavaAstTreeUtil.toNode(e);
    if (BasicJavaAstTreeUtil.isKeyword(node)) {
      return true;
    }
    if (BasicJavaAstTreeUtil.isJavaToken(node)) {
      return BasicJavaAstTreeUtil.is(node, JavaTokenType.IDENTIFIER) || BasicJavaAstTreeUtil.is(node, JavaTokenType.STRING_LITERAL);
    }
    return false;
  }

  @Override
  public List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {
    List<TextRange> ranges = super.select(e, editorText, cursorOffset, editor);
    if (ranges == null) {
      return null;
    }
    if (BasicJavaAstTreeUtil.is(BasicJavaAstTreeUtil.toNode(e), JavaTokenType.STRING_LITERAL)) {
      killRangesBreakingEscapes(e, ranges, e.getTextRange());
    }
    return ranges;
  }

  private static void killRangesBreakingEscapes(PsiElement e, List<TextRange> ranges, TextRange literalRange) {
    for (Iterator<TextRange> iterator = ranges.iterator(); iterator.hasNext(); ) {
      TextRange each = iterator.next();
      if (literalRange.contains(each) &&
          literalRange.getStartOffset() < each.getStartOffset() &&
          e.getText().charAt(each.getStartOffset() - literalRange.getStartOffset() - 1) == '\\') {
        iterator.remove();
      }
    }
  }
}
