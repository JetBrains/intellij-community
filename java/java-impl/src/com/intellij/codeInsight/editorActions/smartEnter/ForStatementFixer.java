/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

/**
 * {@link Fixer} that handles use-cases like below:
 * <b>before:</b>
 * <pre>
 *   void foo() {
 *     for[caret]
 *   }
 * </pre>
 * 
 * <b>after:</b>
 * <pre>
 *   void foo() {
 *     for ([caret]) {
 *       
 *     }
 *   }
 * </pre>
 * 
 * @author Denis Zhdanov
 * @since 5/13/11 4:24 PM
 */
public class ForStatementFixer implements Fixer {

  @Override
  public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (!(psiElement instanceof PsiForStatement)) {
      return;
    }

    PsiForStatement forStatement = (PsiForStatement)psiElement;
    final PsiJavaToken lParenth = forStatement.getLParenth();
    final PsiJavaToken rParenth = forStatement.getRParenth();
    if (lParenth == null || rParenth == null) {
      final TextRange textRange = forStatement.getTextRange();
      editor.getDocument().replaceString(textRange.getStartOffset(), textRange.getEndOffset(), "for () {\n}");
      processor.registerUnresolvedError(textRange.getStartOffset() + "for (".length());
      return;
    }

    final PsiStatement initialization = forStatement.getInitialization();
    if (initialization == null) {
      processor.registerUnresolvedError(lParenth.getTextRange().getEndOffset());
      return;
    }

    final PsiExpression condition = forStatement.getCondition();
    if (condition == null) {
      processor.registerUnresolvedError(initialization.getTextRange().getEndOffset());
      return;
    }
    
    if (forStatement.getUpdate() == null) {
      processor.registerUnresolvedError(condition.getTextRange().getEndOffset());
    } 
  }
}
