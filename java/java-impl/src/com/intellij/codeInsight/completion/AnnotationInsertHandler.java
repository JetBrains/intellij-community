/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.completion;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;

/**
* @author peter
*/
class AnnotationInsertHandler implements InsertHandler<JavaPsiClassReferenceElement> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.AnnotationInsertHandler");
  static final AnnotationInsertHandler INSTANCE = new AnnotationInsertHandler();

  @Override
  public void handleInsert(InsertionContext context, JavaPsiClassReferenceElement item) {
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
    HighlighterIterator iterator = ((EditorEx)myContext.getEditor()).getHighlighter().createIterator(myContext.getStartOffset());
    LOG.assertTrue(iterator.getTokenType() == JavaTokenType.IDENTIFIER);
    iterator.retreat();
    if (iterator.getTokenType() == TokenType.WHITE_SPACE) iterator.retreat();
    return iterator.getTokenType() != JavaTokenType.AT && iterator.getTokenType() != JavaTokenType.DOT;
  }

}
