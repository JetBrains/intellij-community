// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class ClassGetClassInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final CallMatcher OBJECT_GET_CLASS =
    CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_OBJECT, "getClass").parameterCount(0);

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression call) {
        if (!OBJECT_GET_CLASS.test(call)) return;
        // Sometimes people use xyz.getClass() for implicit NPE check. While it's a questionable code style
        // do not warn about such case
        if (call.getParent() instanceof PsiExpressionStatement) return;
        PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
        if (qualifier == null) return;
        PsiType type = qualifier.getType();
        if (!(type instanceof PsiClassType)) return;
        if (!((PsiClassType)type).rawType().equalsToText(CommonClassNames.JAVA_LANG_CLASS)) return;
        holder.registerProblem(Objects.requireNonNull(call.getMethodExpression().getReferenceNameElement()),
                               InspectionsBundle.message("inspection.class.getclass.message"),
                               new RemoveGetClassCallFix(), new ReplaceWithClassClassFix());
      }
    };
  }

  private static class RemoveGetClassCallFix implements LocalQuickFix {
    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionsBundle.message("inspection.class.getclass.fix.remove.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(descriptor.getStartElement(), PsiMethodCallExpression.class);
      if (call == null) return;
      PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
      if (qualifier == null) return;
      new CommentTracker().replaceAndRestoreComments(call, qualifier);
    }
  }

  private static class ReplaceWithClassClassFix implements LocalQuickFix {
    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionsBundle.message("inspection.class.getclass.fix.replace.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(descriptor.getStartElement(), PsiMethodCallExpression.class);
      if (call == null) return;
      CommentTracker ct = new CommentTracker();
      ct.replaceAndRestoreComments(call, "java.lang.Class.class");
    }
  }
}
