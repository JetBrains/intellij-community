/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.RedundantCastUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;

/**
 * User: anna
 */
public class AnonymousCanBeMethodReferenceInspection extends BaseJavaBatchLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance("#" + AnonymousCanBeMethodReferenceInspection.class.getName());

  public boolean reportNotAnnotatedInterfaces = true;

  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return GroupNames.LANGUAGE_LEVEL_SPECIFIC_GROUP_NAME;
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "Anonymous type can be replaced with method reference";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  @Override
  public String getShortName() {
    return "Anonymous2MethodRef";
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel("Report when interface is not annotated with @FunctionalInterface", this, "reportNotAnnotatedInterfaces");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitAnonymousClass(PsiAnonymousClass aClass) {
        super.visitAnonymousClass(aClass);
        if (AnonymousCanBeLambdaInspection.canBeConvertedToLambda(aClass, true, reportNotAnnotatedInterfaces, Collections.emptySet())) {
          final PsiMethod method = aClass.getMethods()[0];
          final PsiCodeBlock body = method.getBody();
          final PsiExpression methodRefCandidate =
            new LambdaCanBeMethodReferenceInspection()
              .canBeMethodReferenceProblem(body, method.getParameterList().getParameters(), aClass.getBaseClassType(),
                                           aClass.getParent());
          if (methodRefCandidate instanceof PsiCallExpression) {
            final PsiCallExpression callExpression = (PsiCallExpression)methodRefCandidate;
            final PsiMethod resolveMethod = callExpression.resolveMethod();
            if (resolveMethod != method &&
                !AnonymousCanBeLambdaInspection.functionalInterfaceMethodReferenced(resolveMethod, aClass, callExpression)) {
              final PsiElement parent = aClass.getParent();
              if (parent instanceof PsiNewExpression) {
                final PsiJavaCodeReferenceElement classReference = ((PsiNewExpression)parent).getClassOrAnonymousClassReference();
                if (classReference != null) {
                  final PsiElement lBrace = aClass.getLBrace();
                  LOG.assertTrue(lBrace != null);
                  final TextRange rangeInElement = new TextRange(0, aClass.getStartOffsetInParent() + lBrace.getStartOffsetInParent());
                  holder.registerProblem(parent,
                                         "Anonymous #ref #loc can be replaced with method reference",
                                         ProblemHighlightType.LIKE_UNUSED_SYMBOL, rangeInElement, new ReplaceWithMethodRefFix());
                }
              }
            }
          }
        }
      }
    };
  }

  private static class ReplaceWithMethodRefFix implements LocalQuickFix {
      @NotNull
      @Override
      public String getFamilyName() {
        return "Replace with method reference";
      }

    @Override
      public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        final PsiElement element = descriptor.getPsiElement();
        if (element instanceof PsiNewExpression) {
          if (!FileModificationService.getInstance().preparePsiElementForWrite(element)) return;
          final PsiAnonymousClass anonymousClass = ((PsiNewExpression)element).getAnonymousClass();
          if (anonymousClass == null) return;
          final PsiMethod[] methods = anonymousClass.getMethods();
          if (methods.length != 1) return;

          final PsiParameter[] parameters = methods[0].getParameterList().getParameters();
          final String methodRefText = LambdaCanBeMethodReferenceInspection
            .convertToMethodReference(methods[0].getBody(), parameters, anonymousClass.getBaseClassType(), anonymousClass.getParent());

          replaceWithMethodReference(project, methodRefText, anonymousClass.getBaseClassType(), anonymousClass.getParent());
        }
      }
  }

  static void replaceWithMethodReference(@NotNull Project project,
                                         String methodRefText,
                                         PsiType castType,
                                         PsiElement replacementTarget) {
    final Collection<PsiComment> comments = ContainerUtil.map(PsiTreeUtil.findChildrenOfType(replacementTarget, PsiComment.class),
                                                              comment -> (PsiComment)comment.copy());

    if (methodRefText != null) {
      final String canonicalText = castType.getCanonicalText();
      final PsiExpression psiExpression = JavaPsiFacade
        .getElementFactory(project).createExpressionFromText("(" + canonicalText + ")" + methodRefText, replacementTarget);

      PsiElement castExpr = replacementTarget.replace(psiExpression);
      if (RedundantCastUtil.isCastRedundant((PsiTypeCastExpression)castExpr)) {
        final PsiExpression operand = ((PsiTypeCastExpression)castExpr).getOperand();
        LOG.assertTrue(operand != null);
        castExpr = castExpr.replace(operand);
      }

      PsiElement anchor = PsiTreeUtil.getParentOfType(castExpr, PsiStatement.class);
      if (anchor == null) {
        anchor = castExpr;
      }
      for (PsiComment comment : comments) {
        anchor.getParent().addBefore(comment, anchor);
      }
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(castExpr);
    }
  }
}
