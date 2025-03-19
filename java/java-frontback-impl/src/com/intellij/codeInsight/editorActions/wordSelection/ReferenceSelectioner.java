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

import static com.intellij.psi.impl.source.BasicJavaElementType.*;

public final class ReferenceSelectioner extends AbstractBasicBackBasicSelectioner {

  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    ASTNode node = BasicJavaAstTreeUtil.toNode(e);
    return BasicJavaAstTreeUtil.is(node, JAVA_CODE_REFERENCE_ELEMENT_SET);
  }

  @Override
  public List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {

    ASTNode node = BasicJavaAstTreeUtil.toNode(e);
    ASTNode endElement = node;
    if (endElement == null) {
      return null;
    }
    while (BasicJavaAstTreeUtil.is(endElement, JAVA_CODE_REFERENCE_ELEMENT_SET) &&
           endElement.getTreeNext() != null) {
      endElement = endElement.getTreeNext();
    }

    if (!(BasicJavaAstTreeUtil.is(endElement, JAVA_CODE_REFERENCE_ELEMENT_SET)) &&
        !(BasicJavaAstTreeUtil.is(endElement.getTreePrev(), REFERENCE_EXPRESSION_SET) &&
          BasicJavaAstTreeUtil.is(endElement, BASIC_EXPRESSION_LIST))) {
      endElement = endElement.getTreePrev();
    }

    ASTNode element = node;
    List<TextRange> result = new ArrayList<>();
    while (BasicJavaAstTreeUtil.is(element, JAVA_CODE_REFERENCE_ELEMENT_SET)) {
      ASTNode firstChild = element.getFirstChildNode();

      ASTNode referenceName = BasicJavaAstTreeUtil.getReferenceNameElement(element);
      if (referenceName != null) {
        result.addAll(expandToWholeLine(editorText, new TextRange(referenceName.getTextRange().getStartOffset(),
                                                                  endElement.getTextRange().getEndOffset())));
        if (BasicJavaAstTreeUtil.is(endElement, JAVA_CODE_REFERENCE_ELEMENT_SET)) {
          final ASTNode endReferenceName = BasicJavaAstTreeUtil.getReferenceNameElement(endElement);
          if (endReferenceName != null) {
            result.addAll(expandToWholeLine(editorText, new TextRange(referenceName.getTextRange().getStartOffset(),
                                                                      endReferenceName.getTextRange().getEndOffset())));
          }
        }
      }

      if (firstChild == null) break;
      element = firstChild;
    }

    TextRange range = new TextRange(element.getTextRange().getStartOffset(),
                                    endElement.getTextRange().getEndOffset());
    result.add(range);
    result.addAll(expandToWholeLine(editorText, range));

    if (!(BasicJavaAstTreeUtil.is(node.getTreeParent(), JAVA_CODE_REFERENCE_ELEMENT_SET))) {
      if (BasicJavaAstTreeUtil.isJavaToken(node.getTreeNext()) ||
          BasicJavaAstTreeUtil.isWhiteSpace(node.getTreeNext()) ||
          BasicJavaAstTreeUtil.is(node.getTreeNext(), BASIC_EXPRESSION_LIST)) {
        List<TextRange> superSelect = super.select(e, editorText, cursorOffset, editor);
        if (superSelect != null) {
          result.addAll(superSelect);
        }
      }
    }

    return result;
  }
}
