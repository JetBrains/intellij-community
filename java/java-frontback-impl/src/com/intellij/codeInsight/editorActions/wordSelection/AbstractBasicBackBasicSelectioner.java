// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.psi.PsiElement;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.BasicJavaAstTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

import static com.intellij.psi.impl.source.BasicElementTypes.BASIC_JAVA_COMMENT_BIT_SET;
import static com.intellij.psi.impl.source.BasicJavaDocElementType.*;
import static com.intellij.psi.impl.source.BasicJavaElementType.*;

public abstract class AbstractBasicBackBasicSelectioner extends ExtendWordSelectionHandlerBase {

  private static Predicate<PsiElement> getElementPredicate() {
    return (e) -> {
      Language language = e.getLanguage();
      return !(language instanceof XMLLanguage || language.isKindOf(XMLLanguage.INSTANCE));
    };
  }

  @Override
  public boolean canSelect(final @NotNull PsiElement e) {
    ASTNode node = BasicJavaAstTreeUtil.toNode(e);
    return
      !BasicJavaAstTreeUtil.is(node, TokenType.WHITE_SPACE) &&
      !BasicJavaAstTreeUtil.is(node, BASIC_JAVA_COMMENT_BIT_SET) &&
      !BasicJavaAstTreeUtil.is(node, BASIC_CODE_BLOCK) &&
      !BasicJavaAstTreeUtil.is(node, BASIC_ARRAY_INITIALIZER_EXPRESSION) &&
      !BasicJavaAstTreeUtil.is(node, BASIC_PARAMETER_LIST) &&
      !BasicJavaAstTreeUtil.is(node, BASIC_EXPRESSION_LIST) &&
      !BasicJavaAstTreeUtil.is(node, BASIC_BLOCK_STATEMENT) &&
      !BasicJavaAstTreeUtil.is(node, JAVA_CODE_REFERENCE_ELEMENT_SET) &&
      !(BasicJavaAstTreeUtil.isJavaToken(node) &&
        !BasicJavaAstTreeUtil.isKeyword(node)) &&
      !BasicJavaAstTreeUtil.is(node, BASIC_DOC_TAG, BASIC_DOC_SNIPPET_TAG, BASIC_DOC_INLINE_TAG) &&
      getElementPredicate().test(e);
  }
}
