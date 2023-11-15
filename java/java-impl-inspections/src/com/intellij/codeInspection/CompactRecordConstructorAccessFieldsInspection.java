// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiBasedModCommandAction;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightRecordMember;
import com.intellij.psi.util.JavaPsiRecordUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class CompactRecordConstructorAccessFieldsInspection extends AbstractBaseJavaLocalInspectionTool {

  @Override
  public boolean runForWholeFile() {
    return true;
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!HighlightingFeature.RECORDS.isAvailable(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitClass(@NotNull PsiClass aClass) {
        if (!aClass.isRecord()) {
          return;
        }
        if (aClass.getRecordComponents().length == 0) {
          return;
        }

        PsiMethod[] methods = aClass.getMethods();
        for (PsiMethod method : methods) {
          if (!JavaPsiRecordUtil.isCompactConstructor(method)) {
            continue;
          }
          List<ProblemInfo> problemInfos = processCompactConstructor(method);
          for (ProblemInfo problemInfo : problemInfos) {
            if (isOnTheFly) {
              LocalQuickFix fix = LocalQuickFix.from(new NavigateToUsageFix(problemInfo.reference));
              holder.registerProblem(problemInfo.callExpression,
                                     JavaBundle.message("inspection.record.compact.constructor.access.fields.display.name"),
                                     fix);
            }
            else {
              holder.registerProblem(problemInfo.callExpression,
                                     JavaBundle.message("inspection.record.compact.constructor.access.fields.display.name"));
            }
          }
        }
      }


      record ProblemInfo(@NotNull PsiCallExpression callExpression, @NotNull PsiReferenceExpression reference) {
      }

      private static List<ProblemInfo> processCompactConstructor(PsiMethod compactConstructor) {
        List<ProblemInfo> result = new ArrayList<>();
        PsiManager psiManager = compactConstructor.getManager();
        PsiClass containingClass = compactConstructor.getContainingClass();
        MultiMap<PsiMethod, PsiCallExpression> nestedMethods = new MultiMap<>();
        final JavaRecursiveElementWalkingVisitor visitor = new JavaRecursiveElementWalkingVisitor() {
          @Override
          public void visitCallExpression(@NotNull PsiCallExpression callExpression) {
            PsiMethod resolvedMethod = callExpression.resolveMethod();
            if (resolvedMethod != null &&
                !resolvedMethod.hasModifierProperty(PsiModifier.STATIC) &&
                psiManager.areElementsEquivalent(resolvedMethod.getContainingClass(), containingClass)) {
              nestedMethods.putValue(resolvedMethod, callExpression);
            }
          }
        };
        compactConstructor.accept(visitor);
        for (PsiMethod method : nestedMethods.keySet()) {
          Ref<PsiReferenceExpression> found = new Ref<>();
          PsiTreeUtil.processElements(method.getBody(), e -> {
            if (e instanceof PsiReferenceExpression referenceExpression) {
              PsiElement resolved = referenceExpression.resolve();
              if (resolved instanceof LightRecordMember) {
                found.set(referenceExpression);
                return false;
              }
            }
            return true;
          });
          if (!found.isNull()) {
            nestedMethods.get(method).forEach(expr -> {
              result.add(new ProblemInfo(expr, found.get()));
            });
          }
        }
        return result;
      }
    };
  }

  private static class NavigateToUsageFix extends PsiBasedModCommandAction<PsiReferenceExpression> {
    private NavigateToUsageFix(@NotNull PsiReferenceExpression reference) {
      super(reference);
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return JavaBundle.message("inspection.record.compact.constructor.access.fields.navigate.usages.family");
    }

    @Override
    protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiReferenceExpression reference) {
      return Presentation.of(JavaBundle.message("inspection.record.compact.constructor.access.fields.navigate.usages.declaration.text",
                                                reference.getCanonicalText()));
    }

    @Override
    protected @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull PsiReferenceExpression reference) {
      return ModCommand.select(reference);
    }
  }
}
