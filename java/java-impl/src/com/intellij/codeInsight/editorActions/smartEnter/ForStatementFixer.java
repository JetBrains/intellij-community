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

import com.intellij.lang.ASTNode;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.tree.JavaJspElementType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

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
      registerErrorOffset(editor, processor, initialization, forStatement);
      return;
    }
    
    if (forStatement.getUpdate() == null) {
      registerErrorOffset(editor, processor, condition, forStatement);
    } 
  }

  /**
   * {@link JavaSmartEnterProcessor#registerUnresolvedError(int) registers target offset} taking care of the situation when
   * current code style implies white space after 'for' part's semicolon.
   * 
   * @param editor            target editor
   * @param processor         target smart enter processor
   * @param lastValidForPart  last valid element of the target 'for' loop
   * @param forStatement      PSI element for the target 'for' loop
   */
  private static void registerErrorOffset(@NotNull Editor editor, @NotNull JavaSmartEnterProcessor processor,
                                          @NotNull PsiElement lastValidForPart, @NotNull PsiForStatement forStatement)
  {
    final Project project = editor.getProject();
    int offset = lastValidForPart.getTextRange().getEndOffset();
    if (project != null && CodeStyleSettingsManager.getSettings(project).getCommonSettings(JavaLanguage.INSTANCE).SPACE_AFTER_COMMA) {
      if (editor.getDocument().getCharsSequence().charAt(lastValidForPart.getTextRange().getEndOffset() - 1) != ';') {
        offset++;
      }
      for (PsiElement element = lastValidForPart.getNextSibling();
           element != null && element != forStatement.getRParenth() && element.getParent() == forStatement;
           element = element.getNextSibling()) {
        final ASTNode node = element.getNode();
        if (node != null && JavaJspElementType.WHITE_SPACE_BIT_SET.contains(node.getElementType()) && element.getTextLength() > 0) {
          offset++;
          break;
        }
      }
    }

    processor.registerUnresolvedError(offset);
  }
}
