// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.BasicJavaAstTreeUtil;

import static com.intellij.psi.impl.source.BasicJavaDocElementType.*;
import static com.intellij.psi.impl.source.BasicJavaElementType.*;


public final class JavaBasicWordSelectionFilter implements Condition<PsiElement> {

  public JavaBasicWordSelectionFilter() {
  }

  @Override
  public boolean value(final PsiElement e) {
    ASTNode node = BasicJavaAstTreeUtil.toNode(e);
    return !BasicJavaAstTreeUtil.is(node, BASIC_CODE_BLOCK) &&
           !BasicJavaAstTreeUtil.is(node, BASIC_ARRAY_INITIALIZER_EXPRESSION) &&
           !BasicJavaAstTreeUtil.is(node, BASIC_PARAMETER_LIST) &&
           !BasicJavaAstTreeUtil.is(node, BASIC_EXPRESSION_LIST) &&
           !BasicJavaAstTreeUtil.is(node, BASIC_BLOCK_STATEMENT) &&
           !BasicJavaAstTreeUtil.is(node, JAVA_CODE_REFERENCE_ELEMENT_SET) &&
           !BasicJavaAstTreeUtil.isJavaToken(node) &&
           !BasicJavaAstTreeUtil.is(node, BASIC_DOC_TAG, BASIC_DOC_SNIPPET_TAG, BASIC_DOC_INLINE_TAG) &&
           !(BasicJavaAstTreeUtil.isDocToken(node) &&
             BasicJavaAstTreeUtil.is(node, JavaDocTokenType.DOC_COMMENT_DATA));
  }
}
