// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInspection.LambdaCanBeMethodReferenceInspection.MethodReferenceCandidate;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.java.JavaBundle;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
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

import java.util.Collection;
import java.util.Collections;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class AnonymousCanBeMethodReferenceInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance(AnonymousCanBeMethodReferenceInspection.class);

  public boolean reportNotAnnotatedInterfaces = true;

  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return InspectionsBundle.message("group.names.language.level.specific.issues.and.migration.aids");
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

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("reportNotAnnotatedInterfaces",
               JavaAnalysisBundle.message("report.when.interface.is.not.annotated.with.functional.interface")));
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitAnonymousClass(@NotNull PsiAnonymousClass aClass) {
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
          if (candidate instanceof PsiCallExpression callExpression) {
            final PsiMethod resolveMethod = callExpression.resolveMethod();
            if (resolveMethod != method &&
                !AnonymousCanBeLambdaInspection.functionalInterfaceMethodReferenced(resolveMethod, aClass, callExpression)) {
              final PsiElement parent = aClass.getParent();
              if (parent instanceof PsiNewExpression newExpression) {
                final PsiJavaCodeReferenceElement classReference = newExpression.getClassOrAnonymousClassReference();
                if (classReference != null) {
                  final PsiElement lBrace = aClass.getLBrace();
                  LOG.assertTrue(lBrace != null);
                  final TextRange rangeInElement = new TextRange(0, aClass.getStartOffsetInParent() + lBrace.getStartOffsetInParent());
                  ProblemHighlightType type;
                  if (methodReferenceCandidate.mySafeQualifier && methodReferenceCandidate.myConformsCodeStyle) {
                    type = ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
                  }
                  else {
                    if (!isOnTheFly) return;
                    type = ProblemHighlightType.INFORMATION;
                  }
                  holder.registerProblem(parent,
                                         JavaBundle.message("inspection.message.anonymous.ref.loc.can.be.replaced.with.method.reference"),
                                         type, rangeInElement, new ReplaceWithMethodRefFix());
                }
              }
            }
          }
        }
      }
    };
  }

  private static class ReplaceWithMethodRefFix extends PsiUpdateModCommandQuickFix {
    @NotNull
    @Override
    public String getFamilyName() {
      return JavaBundle.message("quickfix.family.replace.with.method.reference");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (element instanceof PsiNewExpression newExpression) {
        final PsiAnonymousClass anonymousClass = newExpression.getAnonymousClass();
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

        final String methodRefText =
          LambdaCanBeMethodReferenceInspection.createMethodReferenceText(candidate, functionalInterfaceType, parameters);

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
