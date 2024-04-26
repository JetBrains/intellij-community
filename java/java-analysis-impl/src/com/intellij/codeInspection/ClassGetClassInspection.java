// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class ClassGetClassInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final CallMatcher OBJECT_GET_CLASS =
    CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_OBJECT, "getClass").parameterCount(0);

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
        if (!OBJECT_GET_CLASS.test(call)) return;
        // Sometimes people use xyz.getClass() for implicit NPE check. While it's a questionable code style
        // do not warn about such case
        if (ExpressionUtils.isVoidContext(call)) return;
        PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
        if (qualifier == null) return;
        PsiType type = qualifier.getType();
        if (!PsiTypesUtil.classNameEquals(type, CommonClassNames.JAVA_LANG_CLASS)) return;
        holder.registerProblem(Objects.requireNonNull(call.getMethodExpression().getReferenceNameElement()),
                               JavaAnalysisBundle.message("inspection.class.getclass.message"),
                               new RemoveGetClassCallFix(), new ReplaceWithClassClassFix());
      }
    };
  }

  private static class RemoveGetClassCallFix extends PsiUpdateModCommandQuickFix {
    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return JavaAnalysisBundle.message("inspection.class.getclass.fix.remove.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
      if (call == null) return;
      PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
      if (qualifier == null) return;
      new CommentTracker().replaceAndRestoreComments(call, qualifier);
    }
  }

  private static class ReplaceWithClassClassFix extends PsiUpdateModCommandQuickFix {
    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return JavaAnalysisBundle.message("inspection.class.getclass.fix.replace.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
      if (call == null) return;
      CommentTracker ct = new CommentTracker();
      ct.replaceAndRestoreComments(call, "java.lang.Class.class");
    }
  }
}
