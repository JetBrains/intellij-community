// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.application.options.CodeStyle;
import com.intellij.lang.ASTNode;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
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
 */
public class ForStatementFixer implements Fixer {

  @Override
  public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (!(psiElement instanceof PsiForStatement forStatement)) {
      return;
    }

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
      boolean endlessLoop = initialization instanceof PsiEmptyStatement && forStatement.getUpdate() == null;
      if (!endlessLoop) {
        registerErrorOffset(editor, processor, initialization, forStatement);
      }
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
    if (project != null && CodeStyle.getSettings(editor).getCommonSettings(JavaLanguage.INSTANCE).SPACE_AFTER_COMMA) {
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
