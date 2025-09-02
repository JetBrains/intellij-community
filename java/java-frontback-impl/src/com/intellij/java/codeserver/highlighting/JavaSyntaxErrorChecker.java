// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.highlighting;

import com.intellij.core.JavaPsiBundle;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

public final class JavaSyntaxErrorChecker {
  /**
   * @param element error element to process
   * @return true if this error element should be highlighted as a syntax error
   */
  public static boolean shouldHighlightErrorElement(@NotNull PsiErrorElement element) {
    String description = element.getErrorDescription();
    if (isJavaDocProblem(element)) return false;
    if (description.equals(JavaPsiBundle.message("expected.semicolon"))) {
      PsiElement parent = element.getParent();
      if (parent instanceof PsiExpressionStatement && !PsiUtil.isStatement(parent)) {
        // unterminated expression statement which is not a statement at all:
        // let's report it as not-a-statement instead
        // (see HighlightUtil.checkNotAStatement); it's more visible and provides
        // more useful fixes.
        return false;
      }
      else {
        // reporting missing semicolons after an unclosed string literal is not useful.
        if (isAfterUnclosedStringLiteral(element)) return false;
      }
    }
    else if (description.equals(JavaPsiBundle.message("expected.comma.or.rparen"))) {
      if (isAfterUnclosedStringLiteral(element)) return false;
    }
    else if (description.equals(JavaPsiBundle.message("expected.class.or.interface"))) {
      String text = element.getText();
      if ((text.equals(JavaKeywords.SEALED) || text.equals(JavaKeywords.NON_SEALED) || text.equals(JavaKeywords.VALUE)) &&
          PsiTreeUtil.skipWhitespacesAndCommentsForward(element) instanceof PsiClass) {
        return false;
      }
    }
    return true;
  }

  /**
   * @param element error element to check
   * @return true if this error is javadoc parsing problem. In this case, it's covered by JavadocParsingInspection.
   */
  public static boolean isJavaDocProblem(@NotNull PsiErrorElement element) {
    PsiElement parent = element.getParent();
    return parent instanceof PsiDocComment || parent instanceof PsiDocTag || parent instanceof PsiDocTagValue ||
           parent != null && parent.getNode().getElementType() == JavaDocElementType.DOC_REFERENCE_HOLDER;
  }

  private static boolean isAfterUnclosedStringLiteral(@NotNull PsiErrorElement element) {
    PsiElement prevLeaf = PsiTreeUtil.prevCodeLeaf(element);
    if (prevLeaf instanceof PsiJavaToken token) {
      IElementType type = token.getTokenType();
      if (type == JavaTokenType.STRING_LITERAL) {
        String text = token.getText();
        if (text.length() == 1 || !StringUtil.endsWithChar(text, '"')) {
          return true;
        }
      }
      else if (type == JavaTokenType.CHARACTER_LITERAL) {
        String text = token.getText();
        if (text.length() == 1 || !StringUtil.endsWithChar(text, '\'')) {
          return true;
        }
      }
      else if (type == JavaTokenType.STRING_TEMPLATE_END) {
        String text = token.getText();
        if (text.length() == 1 || !StringUtil.endsWithChar(text, '"')) {
          return true;
        }
      }
    }
    return false;
  }
}
