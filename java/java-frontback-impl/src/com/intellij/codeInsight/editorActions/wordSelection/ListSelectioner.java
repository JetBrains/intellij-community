// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.BasicJavaAstTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.psi.impl.source.BasicJavaElementType.*;

public final class ListSelectioner extends AbstractBasicBackBasicSelectioner {
  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    ASTNode node = BasicJavaAstTreeUtil.toNode(e);
    return BasicJavaAstTreeUtil.is(node, BASIC_PARAMETER_LIST) ||
           BasicJavaAstTreeUtil.is(node, BASIC_EXPRESSION_LIST) ||
           BasicJavaAstTreeUtil.is(node, BASIC_RECORD_HEADER);
  }

  @Override
  public List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {
    ASTNode node = BasicJavaAstTreeUtil.toNode(e);
    if (node == null) {
      return null;
    }

    List<ASTNode> children = BasicJavaAstTreeUtil.getChildren(node);

    int start = 0;
    int end = 0;

    for (ASTNode child : children) {
      if (BasicJavaAstTreeUtil.isJavaToken(child)) {
        if (BasicJavaAstTreeUtil.is(child, JavaTokenType.LPARENTH)) {
          start = BasicJavaAstTreeUtil.getTextOffset(child) + 1;
        }
        if (BasicJavaAstTreeUtil.is(child, JavaTokenType.RPARENTH)) {
          end = BasicJavaAstTreeUtil.getTextOffset(child);
        }
      }
    }

    List<TextRange> result = new ArrayList<>();
    if (start != 0 && end != 0) {
      result.add(new TextRange(start, end));
    }
    return result;
  }
}
