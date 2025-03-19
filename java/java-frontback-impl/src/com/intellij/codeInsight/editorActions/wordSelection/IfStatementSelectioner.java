// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.BasicJavaAstTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.psi.impl.source.BasicJavaElementType.BASIC_IF_STATEMENT;

public final class IfStatementSelectioner extends AbstractBasicBackBasicSelectioner {

  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    return BasicJavaAstTreeUtil.is(BasicJavaAstTreeUtil.toNode(e), BASIC_IF_STATEMENT);
  }

  @Override
  public List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {
    List<TextRange> result = new ArrayList<>(expandToWholeLine(editorText, e.getTextRange(), false));
    ASTNode node = BasicJavaAstTreeUtil.toNode(e);
    if (node == null) {
      return null;
    }
    final ASTNode elseKeyword = BasicJavaAstTreeUtil.getElseElement(node);
    if (elseKeyword != null) {
      final ASTNode then = BasicJavaAstTreeUtil.getThenBranch(node);
      if (then != null) {
        final TextRange thenRange = new TextRange(e.getTextRange().getStartOffset(), then.getTextRange().getEndOffset());
        if (thenRange.contains(cursorOffset)) {
          result.addAll(expandToWholeLine(editorText, thenRange, false));
        }
      }

      result.addAll(expandToWholeLine(editorText,
                                      new TextRange(elseKeyword.getTextRange().getStartOffset(),
                                                    node.getTextRange().getEndOffset()),
                                      false));

      final ASTNode branch = BasicJavaAstTreeUtil.getElseBranch(node);
      if (BasicJavaAstTreeUtil.is(branch, BASIC_IF_STATEMENT)) {
        final ASTNode element = BasicJavaAstTreeUtil.getElseElement(branch);
        if (element != null) {
          final ASTNode elseThen = BasicJavaAstTreeUtil.getThenBranch(branch);
          if (elseThen != null) {
            result.addAll(expandToWholeLine(editorText,
                                            new TextRange(elseKeyword.getTextRange().getStartOffset(),
                                                          elseThen.getTextRange().getEndOffset()),
                                            false));
          }
        }
      }
    }

    return result;
  }
}
