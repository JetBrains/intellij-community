// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.tree.IElementType;

/**
 * @author Bas Leijdekkers
 */
public final class JavaMatchUtil {

  private JavaMatchUtil() {}

  public static String getCommentText(PsiComment comment) {
    if (comment instanceof PsiDocComment) {
      final PsiDocComment docComment = (PsiDocComment)comment;
      final StringBuilder result = new StringBuilder();
      for (PsiElement element : docComment.getDescriptionElements()) {
        result.append(element.getText());
      }
      return result.toString();
    }
    else {
      final IElementType type = comment.getTokenType();
      final String text = comment.getText();
      return (type == JavaTokenType.END_OF_LINE_COMMENT)
             ? StringUtil.trimStart(text, "//")
             : StringUtil.trimEnd(text.substring(2), "*/");
    }
  }
}
