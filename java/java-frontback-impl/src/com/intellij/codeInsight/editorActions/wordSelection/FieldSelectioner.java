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
import java.util.List;

import static com.intellij.psi.impl.source.BasicJavaElementType.BASIC_ENUM_CONSTANT;
import static com.intellij.psi.impl.source.BasicJavaElementType.BASIC_FIELD;

public final class FieldSelectioner extends WordSelectioner {

  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    return BasicJavaAstTreeUtil.is(BasicJavaAstTreeUtil.toNode(e), BASIC_FIELD, BASIC_ENUM_CONSTANT) &&
           e.getLanguage() == JavaLanguage.INSTANCE;
  }

  private static void addRangeElem(final List<? super TextRange> result,
                                   CharSequence editorText,
                                   final ASTNode first,
                                   final int end) {
    if (first != null) {
      result.addAll(expandToWholeLine(editorText,
                                      new TextRange(first.getTextRange().getStartOffset(), end)));
    }
  }

  @Override
  public List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {
    List<TextRange> result = super.select(e, editorText, cursorOffset, editor);
    if (result == null) {
      return null;
    }
    ASTNode node = BasicJavaAstTreeUtil.toNode(e);
    if (node == null) {
      return null;
    }
    TextRange fieldRange = node.getTextRange();
    ASTNode nameId = BasicJavaAstTreeUtil.getNameIdentifier(node);
    if (nameId == null) return new ArrayList<>();
    TextRange nameRange = nameId.getTextRange();
    ASTNode last = BasicJavaAstTreeUtil.getInitializer(node);
    int end = last == null ? nameRange.getEndOffset() : last.getTextRange().getEndOffset();

    ASTNode comment = BasicJavaAstTreeUtil.getDocComment(node);
    if (comment != null) {
      TextRange commentTextRange = comment.getTextRange();
      addRangeElem(result, editorText, comment, commentTextRange.getEndOffset());
    }
    addRangeElem(result, editorText, nameId, end);
    addRangeElem(result, editorText, BasicJavaAstTreeUtil.getTypeElement(node), nameRange.getEndOffset());
    addRangeElem(result, editorText, BasicJavaAstTreeUtil.getModifierList(node), fieldRange.getEndOffset());
    result.addAll(expandToWholeLine(editorText, fieldRange));
    return result;
  }
}
