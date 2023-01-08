// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

class AnnotationInsertHandler implements InsertHandler<JavaPsiClassReferenceElement> {
  private static final Logger LOG = Logger.getInstance(AnnotationInsertHandler.class);
  static final AnnotationInsertHandler INSTANCE = new AnnotationInsertHandler();

  @Override
  public void handleInsert(@NotNull InsertionContext context, @NotNull JavaPsiClassReferenceElement item) {
    JavaClassNameInsertHandler.JAVA_CLASS_INSERT_HANDLER.handleInsert(context, item);

    PsiFile file = context.getFile();

    PsiElement elementAt = file.findElementAt(context.getStartOffset());
    final PsiElement parentElement = elementAt != null ? elementAt.getParent():null;

    if (elementAt instanceof PsiIdentifier &&
        (PsiTreeUtil.getParentOfType(elementAt, PsiAnnotationParameterList.class) != null ||
         parentElement instanceof PsiErrorElement && parentElement.getParent() instanceof PsiJavaFile // top level annotation without @
        )
        && isAtTokenNeeded(context)) {
      int expectedOffsetForAtToken = elementAt.getTextRange().getStartOffset();
      context.getDocument().insertString(expectedOffsetForAtToken, "@");
    }

    if (JavaClassNameInsertHandler.shouldHaveAnnotationParameters(item.getObject())) {
      JavaCompletionUtil.insertParentheses(context, item, false, true);
    }
  }

  private static boolean isAtTokenNeeded(InsertionContext myContext) {
    HighlighterIterator iterator = myContext.getEditor().getHighlighter().createIterator(myContext.getStartOffset());
    LOG.assertTrue(iterator.getTokenType() == JavaTokenType.IDENTIFIER);
    iterator.retreat();
    if (iterator.getTokenType() == TokenType.WHITE_SPACE) iterator.retreat();
    return iterator.getTokenType() != JavaTokenType.AT && iterator.getTokenType() != JavaTokenType.DOT;
  }

}
