/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInspection;

import com.intellij.lang.Commenter;
import com.intellij.lang.LanguageCommenters;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.PsiJavaElementPattern;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.PsiJavaPatterns.*;

/**
 * @author Dmitry Batkovich
 */
public class BlockMarkerCommentsInspection extends BaseJavaBatchLocalInspectionTool {
  private static final PsiJavaElementPattern ANONYMOUS_CLASS_MARKER_PATTERN = psiElement().
    withParent(psiElement(PsiDeclarationStatement.class, PsiExpressionStatement.class))
    .afterSiblingSkipping(or(psiElement(PsiWhiteSpace.class), psiElement(PsiJavaToken.class).with(new PatternCondition<PsiJavaToken>(null) {
                            @Override
                            public boolean accepts(@NotNull final PsiJavaToken psiJavaToken, final ProcessingContext context) {
                              return psiJavaToken.getTokenType().equals(JavaTokenType.SEMICOLON);
                            }
                          })),
                          psiElement(PsiLocalVariable.class, PsiAssignmentExpression.class)
                            .withChild(psiElement(PsiNewExpression.class).withChild(psiElement(PsiAnonymousClass.class))));
  private static final PsiJavaElementPattern CLASS_MARKER_PATTERN = psiElement().
    withParent(PsiClass.class).
    afterSiblingSkipping(psiElement(PsiWhiteSpace.class), psiElement(PsiJavaToken.class).with(new PatternCondition<PsiJavaToken>(null) {
      @Override
      public boolean accepts(@NotNull final PsiJavaToken token, final ProcessingContext context) {
        return JavaTokenType.RBRACE.equals(token.getTokenType());
      }
    }));
  private static final PsiJavaElementPattern TRY_CATCH_MARKER_PATTERN = psiElement().
    withParent(PsiTryStatement.class).
    afterSiblingSkipping(psiElement(PsiWhiteSpace.class), psiElement(PsiCodeBlock.class, PsiCatchSection.class));
  private static final PsiJavaElementPattern LOOP_OR_IF_MARKER =
    psiElement().afterSiblingSkipping(psiElement(PsiWhiteSpace.class), psiElement(PsiCodeBlock.class)).
      withParent(psiElement(PsiBlockStatement.class).withParent(psiElement(PsiLoopStatement.class, PsiIfStatement.class)));
  private static final PsiJavaElementPattern METHOD_MARKER_PATTERN =
    psiElement().withParent(PsiMethod.class).afterSiblingSkipping(psiElement(PsiWhiteSpace.class), psiElement(PsiCodeBlock.class));
  
  private static final ElementPattern MARKER_PATTERN = or(ANONYMOUS_CLASS_MARKER_PATTERN,
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
      public void visitComment(final PsiComment element) {
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
        final String commentText = rawCommentText.trim().toLowerCase();
        if (!commentText.startsWith(END_WORD) || StringUtil.split(commentText, " ").size() > 3) {
          return;
        }
        if (MARKER_PATTERN.accepts(element)) {
          holder.registerProblem(element, "Redundant block marker", new LocalQuickFix() {
            @NotNull
            @Override
            public String getName() {
              return getFamilyName();
            }

            @NotNull
            @Override
            public String getFamilyName() {
              return "Remove block marker comments";
            }

            @Override
            public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
              descriptor.getPsiElement().delete();
            }
          });
        }
      }
    };
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "Block marker comment";
  }
}
