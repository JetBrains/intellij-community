// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.LambdaCanBeMethodReferenceInspection.MethodReferenceCandidate;
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

public class AnonymousCanBeMethodReferenceInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance(AnonymousCanBeMethodReferenceInspection.class);

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
          MethodReferenceCandidate methodReferenceCandidate =
            LambdaCanBeMethodReferenceInspection.extractMethodReferenceCandidateExpression(body);
          if (methodReferenceCandidate == null) return;
          final PsiExpression candidate =
            LambdaCanBeMethodReferenceInspection
              .canBeMethodReferenceProblem(method.getParameterList().getParameters(), aClass.getBaseClassType(), aClass.getParent(),
                                           methodReferenceCandidate.myExpression);
          if (candidate instanceof PsiCallExpression) {
            final PsiCallExpression callExpression = (PsiCallExpression)candidate;
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
                  ProblemHighlightType type;
                  if (methodReferenceCandidate.mySafeQualifier && methodReferenceCandidate.myConformsCodeStyle) {
                    type = ProblemHighlightType.LIKE_UNUSED_SYMBOL;
                  }
                  else {
                    if (!isOnTheFly) return;
                    type = ProblemHighlightType.INFORMATION;
                  }
                  holder.registerProblem(parent,
                                         "Anonymous #ref #loc can be replaced with method reference",
                                         type, rangeInElement, new ReplaceWithMethodRefFix());
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
          final PsiAnonymousClass anonymousClass = ((PsiNewExpression)element).getAnonymousClass();
          if (anonymousClass == null) return;
          final PsiMethod[] methods = anonymousClass.getMethods();
          if (methods.length != 1) return;

          final PsiParameter[] parameters = methods[0].getParameterList().getParameters();
          final PsiType functionalInterfaceType = anonymousClass.getBaseClassType();
          MethodReferenceCandidate methodRefCandidate =
            LambdaCanBeMethodReferenceInspection.extractMethodReferenceCandidateExpression(methods[0].getBody());
          if (methodRefCandidate == null) return;
          final PsiExpression candidate = LambdaCanBeMethodReferenceInspection
            .canBeMethodReferenceProblem(parameters, functionalInterfaceType, anonymousClass.getParent(), methodRefCandidate.myExpression);

          final String methodRefText = LambdaCanBeMethodReferenceInspection.createMethodReferenceText(candidate, functionalInterfaceType, parameters);

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
