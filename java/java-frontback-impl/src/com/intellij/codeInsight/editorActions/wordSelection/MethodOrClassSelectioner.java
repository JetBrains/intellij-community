// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.lang.ASTNode;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.BasicJavaAstTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.intellij.psi.impl.source.BasicElementTypes.BASIC_JAVA_COMMENT_BIT_SET;
import static com.intellij.psi.impl.source.BasicElementTypes.BASIC_JAVA_COMMENT_OR_WHITESPACE_BIT_SET;
import static com.intellij.psi.impl.source.BasicJavaDocElementType.BASIC_DOC_COMMENT;
import static com.intellij.psi.impl.source.BasicJavaElementType.*;

public final class MethodOrClassSelectioner extends AbstractBasicBackBasicSelectioner {

  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    ASTNode node = BasicJavaAstTreeUtil.toNode(e);
    return (
             BasicJavaAstTreeUtil.is(node, CLASS_SET) &&
             !BasicJavaAstTreeUtil.is(node, BASIC_TYPE_PARAMETER) ||
             BasicJavaAstTreeUtil.is(node, BASIC_METHOD)) &&
           e.getLanguage() == JavaLanguage.INSTANCE;
  }

  @Override
  public List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {
    List<TextRange> result = new ArrayList<>();

    ASTNode node = BasicJavaAstTreeUtil.toNode(e);
    if (node == null) {
      return result;
    }
    ASTNode firstChild = node.getFirstChildNode();
    List<ASTNode> children = BasicJavaAstTreeUtil.getChildren(node);
    int i = 1;

    if (BasicJavaAstTreeUtil.is(firstChild, BASIC_DOC_COMMENT)) {
      while (BasicJavaAstTreeUtil.isWhiteSpace(children.get(i))) {
        i++;
      }

      TextRange range = new TextRange(children.get(i).getTextRange().getStartOffset(), e.getTextRange().getEndOffset());
      result.add(range);
      result.addAll(expandToWholeLinesWithBlanks(editorText, range));

      range = firstChild.getTextRange();
      result.addAll(expandToWholeLinesWithBlanks(editorText, range));

      firstChild = children.get(i++);
    }
    if (BasicJavaAstTreeUtil.is(firstChild, BASIC_JAVA_COMMENT_BIT_SET)) {
      while (BasicJavaAstTreeUtil.is(children.get(i), BASIC_JAVA_COMMENT_OR_WHITESPACE_BIT_SET)) {
        i++;
      }
      ASTNode last = BasicJavaAstTreeUtil.isWhiteSpace(children.get(i - 1)) ? children.get(i - 2) : children.get(i - 1);
      TextRange range = new TextRange(firstChild.getTextRange().getStartOffset(), last.getTextRange().getEndOffset());
      if (range.contains(cursorOffset)) {
        result.addAll(expandToWholeLinesWithBlanks(editorText, range));
      }

      range = new TextRange(children.get(i).getTextRange().getStartOffset(), e.getTextRange().getEndOffset());
      result.add(range);
      result.addAll(expandToWholeLinesWithBlanks(editorText, range));
    }

    result.add(node.getTextRange());
    result.addAll(expandToWholeLinesWithBlanks(editorText, node.getTextRange()));

    if (BasicJavaAstTreeUtil.is(node, CLASS_SET)) {
      result.addAll(selectWithTypeParameters(node));
      result.addAll(selectBetweenBracesLines(children, editorText));
    }
    if (BasicJavaAstTreeUtil.is(node, BASIC_ANONYMOUS_CLASS)) {
      result.addAll(selectWholeBlock(node));
    }

    return result;
  }

  private static Collection<TextRange> selectWithTypeParameters(@NotNull ASTNode astClass) {
    final ASTNode identifier = BasicJavaAstTreeUtil.getNameIdentifier(astClass);
    final ASTNode list = BasicJavaAstTreeUtil.getTypeParameterList(astClass);
    if (identifier != null && list != null) {
      return Collections.singletonList(new TextRange(identifier.getTextRange().getStartOffset(), list.getTextRange().getEndOffset()));
    }
    return Collections.emptyList();
  }

  private static Collection<TextRange> selectBetweenBracesLines(List<ASTNode> children,
                                                                @NotNull CharSequence editorText) {
    int start = CodeBlockOrInitializerSelectioner.findOpeningBrace(children);
    // in non-Java PsiClasses, there can be no opening brace
    if (start != 0) {
      int end = CodeBlockOrInitializerSelectioner.findClosingBrace(children, start);

      return expandToWholeLinesWithBlanks(editorText, new TextRange(start, end));
    }
    return Collections.emptyList();
  }

  private static Collection<TextRange> selectWholeBlock(ASTNode clazz) {
    ASTNode lBrace = BasicJavaAstTreeUtil.getLBrace(clazz);
    ASTNode rBrace = BasicJavaAstTreeUtil.getRBrace(clazz);
    if (lBrace != null && rBrace != null) {
      return Collections.singleton(new TextRange(lBrace.getTextRange().getStartOffset(), rBrace.getTextRange().getEndOffset()));
    }
    return Collections.emptyList();
  }
}
