// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CommentTracker;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.util.ObjectUtils.tryCast;

public class RedundantExplicitCloseInspection extends AbstractBaseJavaLocalInspectionTool {
  CallMatcher CLOSE = CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_AUTO_CLOSEABLE, "close").parameterCount(0);

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitTryStatement(PsiTryStatement statement) {
        PsiResourceList resourceList = statement.getResourceList();
        if (resourceList == null) return;

        PsiCodeBlock tryBlock = statement.getTryBlock();
        if (tryBlock == null) return;
        PsiStatement[] statements = tryBlock.getStatements();
        if(statements.length == 0) return;
        PsiStatement last = statements[statements.length - 1];
        PsiExpressionStatement expressionStatement = tryCast(last, PsiExpressionStatement.class);
        if(expressionStatement == null) return;
        PsiMethodCallExpression call = tryCast(expressionStatement.getExpression(), PsiMethodCallExpression.class);
        if (!CLOSE.test(call)) return;
        PsiReferenceExpression reference = tryCast(call.getMethodExpression().getQualifierExpression(), PsiReferenceExpression.class);
        if(reference == null) return;
        PsiVariable variable = tryCast(reference.resolve(), PsiVariable.class);
        if(variable == null) return;
        boolean isReferenceToResourceVariable = StreamEx.of(resourceList.iterator())
                            .anyMatch(element -> {
                              if (element instanceof PsiResourceVariable && variable == element) {
                                return true;
                              }
                              else {
                                PsiReferenceExpression ref = tryCast(element, PsiReferenceExpression.class);
                                if (ref == null) return false;
                                return ref.resolve() == variable;
                              }
                            });
        if(!isReferenceToResourceVariable) return;
        holder.registerProblem(last, InspectionsBundle.message("inspection.redundant.explicit.close"),
                               ProblemHighlightType.LIKE_UNUSED_SYMBOL, new DeleteRedundantCloseFix());

      }
    };
  }

  private static class DeleteRedundantCloseFix implements LocalQuickFix {
    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionsBundle.message("inspection.redundant.explicit.close.fix.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getStartElement();
      new CommentTracker().deleteAndRestoreComments(element);
    }
  }
}
