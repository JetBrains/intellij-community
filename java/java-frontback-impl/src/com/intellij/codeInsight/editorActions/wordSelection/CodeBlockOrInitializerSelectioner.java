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

public final class CodeBlockOrInitializerSelectioner extends AbstractBasicBackBasicSelectioner {
  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    ASTNode node = BasicJavaAstTreeUtil.toNode(e);
    return BasicJavaAstTreeUtil.is(node, BASIC_CODE_BLOCK) ||
           BasicJavaAstTreeUtil.is(node, BASIC_ARRAY_INITIALIZER_EXPRESSION) ||
           BasicJavaAstTreeUtil.is(node, CLASS_SET) &&
           !BasicJavaAstTreeUtil.is(node, BASIC_TYPE_PARAMETER);
  }

  @Override
  public List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {
    List<TextRange> result = new ArrayList<>();
    ASTNode node = BasicJavaAstTreeUtil.toNode(e);
    if (node == null) {
      return null;
    }
    result.add(getElementRange(node));


    List<ASTNode> children = BasicJavaAstTreeUtil.getChildren(node);
    if (!children.isEmpty()) {
      int start = findOpeningBrace(children);

      // in non-Java PsiClasses, there can be no opening brace
      if (start != 0) {
        int end = findClosingBrace(children, start);
        result.addAll(expandToWholeLine(editorText, new TextRange(start, end)));
      }
    }

    return result;
  }

  public TextRange getElementRange(@NotNull ASTNode astNode) {
    if (BasicJavaAstTreeUtil.is(astNode, CLASS_SET)) {
      ASTNode lBrace = BasicJavaAstTreeUtil.getLBrace(astNode);
      ASTNode rBrace = BasicJavaAstTreeUtil.getRBrace(astNode);
      if (lBrace != null && rBrace != null) {
        return new TextRange(BasicJavaAstTreeUtil.getTextOffset(lBrace), rBrace.getTextRange().getEndOffset());
      }
    }

    return astNode.getTextRange();
  }

  public static int findOpeningBrace(List<ASTNode> children) {
    int start = 0;
    for (int i = 0; i < children.size(); i++) {
      ASTNode child = children.get(i);

      if (BasicJavaAstTreeUtil.is(child, JavaTokenType.LBRACE)) {
        int j = i + 1;

        while (BasicJavaAstTreeUtil.isWhiteSpace(children.get(j))) {
          j++;
        }

        start = children.get(j).getTextRange().getStartOffset();
      }
    }
    return start;
  }

  public static int findClosingBrace(List<ASTNode> children, int startOffset) {
    int end = children.get(children.size() - 1).getTextRange().getEndOffset();
    for (int i = 0; i < children.size(); i++) {
      ASTNode child = children.get(i);

      if (BasicJavaAstTreeUtil.is(child, JavaTokenType.RBRACE)) {
        int j = i - 1;

        while (BasicJavaAstTreeUtil.isWhiteSpace(children.get(j)) && children.get(j).getTextRange().getStartOffset() > startOffset) {
          j--;
        }

        end = children.get(j).getTextRange().getEndOffset();
      }
    }
    return end;
  }
}
