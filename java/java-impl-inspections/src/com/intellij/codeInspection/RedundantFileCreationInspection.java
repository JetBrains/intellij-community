// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public final class RedundantFileCreationInspection extends AbstractBaseJavaLocalInspectionTool {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {

      @Override
      public void visitNewExpression(@NotNull PsiNewExpression newExpression) {
        super.visitNewExpression(newExpression);

        final List<String> targetTypes = Arrays.asList(
          CommonClassNames.JAVA_IO_FILE_INPUT_STREAM, CommonClassNames.JAVA_IO_FILE_OUTPUT_STREAM,
          CommonClassNames.JAVA_IO_FILE_READER, CommonClassNames.JAVA_IO_FILE_WRITER,
          CommonClassNames.JAVA_IO_PRINT_STREAM, CommonClassNames.JAVA_IO_PRINT_WRITER,
          CommonClassNames.JAVA_UTIL_FORMATTER
        );

        final PsiType type = newExpression.getType();
        if (type == null || !targetTypes.contains(type.getCanonicalText())) {
          return;
        }

        final PsiMethod constructor = newExpression.resolveConstructor();
        if (constructor == null) return;
        final PsiParameter[] params = constructor.getParameterList().getParameters();

        if (params.length == 0 || !TypeUtils.typeEquals(CommonClassNames.JAVA_IO_FILE, params[0].getType())) return;

        final PsiExpressionList argList = newExpression.getArgumentList();
        if (argList == null) return;

        final PsiExpression[] args = argList.getExpressions();
        if (args.length == 0) return;

        PsiNewExpression arg = ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(args[0]), PsiNewExpression.class);
        if (arg == null) return;

        final PsiMethod fileConstructor = arg.resolveConstructor();
        if (fileConstructor == null) return;

        final PsiParameter[] fileParams = fileConstructor.getParameterList().getParameters();
        if (fileParams.length != 1) return;

        if (!TypeUtils.isJavaLangString(fileParams[0].getType())) return;

        PsiExpressionList fileArgList = arg.getArgumentList();
        if (fileArgList == null) return;

        if (!canReplacedWithConstructorTakesFilename(constructor)) return;

        holder.registerProblem(arg,
                               JavaBundle.message("inspection.redundant.file.creation.description"),
                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                               new TextRange(0, fileArgList.getStartOffsetInParent()),
                               new DeleteRedundantFileCreationFix());

      }
    };
  }

  private static boolean canReplacedWithConstructorTakesFilename(PsiMethod method) {
    PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) return false;

    List<PsiType> methodParams = ContainerUtil.map(method.getParameterList().getParameters(), param -> param.getType());
    assert !methodParams.isEmpty();

    for (final PsiMethod candidate : containingClass.getMethods()) {
      if (!candidate.isConstructor() || candidate.equals(method)) continue;
      List<PsiType> candidateParams = ContainerUtil.map(candidate.getParameterList().getParameters(), param -> param.getType());
      if (candidateParams.size() != methodParams.size()) continue;
      if (TypeUtils.isJavaLangString(candidateParams.get(0)) &&
          methodParams.subList(1, methodParams.size()).equals(candidateParams.subList(1, candidateParams.size()))) {
        return true;
      }
    }
    return false;
  }

  private static class DeleteRedundantFileCreationFix extends PsiUpdateModCommandQuickFix {
    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return JavaBundle.message("inspection.redundant.file.creation.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (!(element instanceof PsiNewExpression newExpression)) return;

      final PsiExpressionList argList = newExpression.getArgumentList();
      if (argList == null) return;

      final PsiExpression[] args = argList.getExpressions();
      if (args.length != 1) return;

      CommentTracker commentTracker = new CommentTracker();

      commentTracker.replace(newExpression, args[0]);
    }
  }
}
