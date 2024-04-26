// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.BaseJavaJspElementType;
import com.intellij.application.options.CodeStyle;
import com.intellij.lang.ASTNode;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.impl.source.BasicJavaAstTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.impl.source.BasicJavaElementType.BASIC_EMPTY_STATEMENT;
import static com.intellij.psi.impl.source.BasicJavaElementType.BASIC_FOR_STATEMENT;

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
  public void apply(Editor editor, AbstractBasicJavaSmartEnterProcessor processor, @NotNull ASTNode astNode) throws IncorrectOperationException {
    if (!(BasicJavaAstTreeUtil.is(astNode, BASIC_FOR_STATEMENT))) {
      return;
    }

    final ASTNode lParenth = BasicJavaAstTreeUtil.getLParenth(astNode);
    final ASTNode rParenth = BasicJavaAstTreeUtil.getRParenth(astNode);
    if (lParenth == null || rParenth == null) {
      final TextRange textRange = astNode.getTextRange();
      editor.getDocument().replaceString(textRange.getStartOffset(), textRange.getEndOffset(), "for () {\n}");
      processor.registerUnresolvedError(textRange.getStartOffset() + "for (".length());
      return;
    }

    final ASTNode initialization = BasicJavaAstTreeUtil.getForInitialization(astNode);
    if (initialization == null) {
      processor.registerUnresolvedError(lParenth.getTextRange().getEndOffset());
      return;
    }

    final ASTNode condition = BasicJavaAstTreeUtil.getForCondition(astNode);
    if (condition == null) {
      boolean endlessLoop = BasicJavaAstTreeUtil.is(initialization, BASIC_EMPTY_STATEMENT) &&
                            BasicJavaAstTreeUtil.getForUpdate(astNode) == null;
      if (!endlessLoop) {
        registerErrorOffset(editor, processor, initialization, astNode);
      }
      return;
    }

    if (BasicJavaAstTreeUtil.getForUpdate(astNode) == null) {
      registerErrorOffset(editor, processor, condition, astNode);
    }
  }

  /**
   * {@link JavaSmartEnterProcessor#registerUnresolvedError(int) registers target offset} taking care of the situation when
   * current code style implies white space after 'for' part's semicolon.
   *
   * @param editor           target editor
   * @param processor        target smart enter processor
   * @param lastValidForPart last valid element of the target 'for' loop
   * @param forStatement     PSI element for the target 'for' loop
   */
  private static void registerErrorOffset(@NotNull Editor editor, @NotNull AbstractBasicJavaSmartEnterProcessor processor,
                                          @NotNull ASTNode lastValidForPart, @NotNull ASTNode forStatement) {
    final Project project = editor.getProject();
    int offset = lastValidForPart.getTextRange().getEndOffset();
    if (project != null && CodeStyle.getSettings(editor).getCommonSettings(JavaLanguage.INSTANCE).SPACE_AFTER_COMMA) {
      if (editor.getDocument().getCharsSequence().charAt(lastValidForPart.getTextRange().getEndOffset() - 1) != ';') {
        offset++;
      }
      for (ASTNode element = lastValidForPart.getTreeNext();
           element != null && element != BasicJavaAstTreeUtil.getRParenth(forStatement) && element.getTreeParent() == forStatement;
           element = element.getTreeNext()) {
        if (isWhiteSpaceIncludingJsp(element) && element.getTextLength() > 0) {
          offset++;
          break;
        }
      }
    }

    processor.registerUnresolvedError(offset);
  }

  private static boolean isWhiteSpaceIncludingJsp(@NotNull ASTNode node) {
    return BaseJavaJspElementType.WHITE_SPACE_BIT_SET.contains(node.getElementType());
  }
}
