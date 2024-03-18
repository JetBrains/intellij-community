// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.lang.Commenter;
import com.intellij.lang.LanguageCommenters;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.PsiJavaElementPattern;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.PsiJavaPatterns.or;
import static com.intellij.patterns.PsiJavaPatterns.psiElement;

/**
 * @author Dmitry Batkovich
 */
public final class BlockMarkerCommentsInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final PsiJavaElementPattern.Capture<PsiElement> ANONYMOUS_CLASS_MARKER_PATTERN = psiElement().
    withParent(psiElement(PsiDeclarationStatement.class, PsiExpressionStatement.class))
    .afterSiblingSkipping(or(psiElement(PsiWhiteSpace.class), psiElement(PsiJavaToken.class).with(new PatternCondition<>(null) {
                            @Override
                            public boolean accepts(@NotNull final PsiJavaToken psiJavaToken, final ProcessingContext context) {
                              return psiJavaToken.getTokenType().equals(JavaTokenType.SEMICOLON);
                            }
                          })),
                          psiElement(PsiLocalVariable.class, PsiAssignmentExpression.class)
                            .withChild(psiElement(PsiNewExpression.class).withChild(psiElement(PsiAnonymousClass.class))));
  private static final PsiJavaElementPattern.Capture<PsiElement> CLASS_MARKER_PATTERN = psiElement().
    withParent(PsiClass.class).
    afterSiblingSkipping(psiElement(PsiWhiteSpace.class), psiElement(PsiJavaToken.class).with(new PatternCondition<>(null) {
      @Override
      public boolean accepts(@NotNull final PsiJavaToken token, final ProcessingContext context) {
        return JavaTokenType.RBRACE.equals(token.getTokenType());
      }
    }));
  private static final PsiJavaElementPattern.Capture<PsiElement> TRY_CATCH_MARKER_PATTERN = psiElement().
    withParent(PsiTryStatement.class).
    afterSiblingSkipping(psiElement(PsiWhiteSpace.class), psiElement(PsiCodeBlock.class, PsiCatchSection.class));
  private static final PsiJavaElementPattern.Capture<PsiElement> LOOP_OR_IF_MARKER =
    psiElement().afterSiblingSkipping(psiElement(PsiWhiteSpace.class), psiElement(PsiCodeBlock.class)).
      withParent(psiElement(PsiBlockStatement.class).withParent(psiElement(PsiLoopStatement.class, PsiIfStatement.class)));
  private static final PsiJavaElementPattern.Capture<PsiElement> METHOD_MARKER_PATTERN =
    psiElement().withParent(PsiMethod.class).afterSiblingSkipping(psiElement(PsiWhiteSpace.class), psiElement(PsiCodeBlock.class));

  private static final ElementPattern<PsiElement> MARKER_PATTERN = or(ANONYMOUS_CLASS_MARKER_PATTERN,
                                                                      CLASS_MARKER_PATTERN,
                                                                      TRY_CATCH_MARKER_PATTERN,
                                                                      LOOP_OR_IF_MARKER,
                                                                      METHOD_MARKER_PATTERN);

  private static final String END_WORD = "end";

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new PsiElementVisitor() {
      @Override
      public void visitComment(@NotNull final PsiComment element) {
        final IElementType tokenType = element.getTokenType();
        if (!(tokenType.equals(JavaTokenType.END_OF_LINE_COMMENT))) {
          return;
        }
        final Commenter commenter = LanguageCommenters.INSTANCE.forLanguage(element.getLanguage());
        String rawCommentText = element.getText();
        final String prefix = commenter.getLineCommentPrefix();
        if (prefix != null && rawCommentText.startsWith(prefix)) {
          rawCommentText = rawCommentText.substring(prefix.length());
        }
        final String commentText = StringUtil.toLowerCase(rawCommentText.trim());
        if (!commentText.startsWith(END_WORD) || StringUtil.split(commentText, " ").size() > 3) {
          return;
        }
        if (MARKER_PATTERN.accepts(element)) {
          holder.registerProblem(element, JavaAnalysisBundle.message("redundant.block.marker"), new DeleteBlockMarkerCommentFix());
        }
      }
    };
  }

  private static class DeleteBlockMarkerCommentFix extends PsiUpdateModCommandQuickFix {
    @NotNull
    @Override
    public String getFamilyName() {
      return JavaAnalysisBundle.message("remove.block.marker.comments");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      element.delete();
    }
  }
}
