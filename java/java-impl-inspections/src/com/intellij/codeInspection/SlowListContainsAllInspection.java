// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.TestUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import static com.siyeh.ig.callMatcher.CallMatcher.instanceCall;

public class SlowListContainsAllInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final CallMatcher LIST_CONTAINS_ALL =
    instanceCall(CommonClassNames.JAVA_UTIL_LIST, "containsAll").parameterTypes(CommonClassNames.JAVA_UTIL_COLLECTION);

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression call) {
        if (TestUtils.isInTestCode(call)) return;
        super.visitMethodCallExpression(call);
        if (!LIST_CONTAINS_ALL.test(call)) return;
        PsiReferenceExpression expression = call.getMethodExpression();
        final PsiExpression qualifier = ExpressionUtils.getEffectiveQualifier(expression);
        if (qualifier == null) return;
        final LongRangeSet listSizeRange = SlowAbstractSetRemoveAllInspection.getSizeRangeOfCollection(qualifier);
        if (listSizeRange.isEmpty() || listSizeRange.max() <= 5) return;
        holder.registerProblem(call,
                               JavaBundle.message("inspection.slow.list.contains.all.description"),
                               ProblemHighlightType.WARNING,
                               expression.getTextRangeInParent(),
                               new ReplaceWithHashSetContainsAllFix(qualifier.getText()));
      }
    };
  }

  private static class ReplaceWithHashSetContainsAllFix implements LocalQuickFix {
    private final String myCollectionText;

    ReplaceWithHashSetContainsAllFix(String collectionText) {
      myCollectionText = collectionText;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return JavaBundle.message("inspection.slow.list.contains.all.fix.name", myCollectionText);
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return JavaBundle.message("inspection.slow.list.contains.all.fix.family.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiMethodCallExpression call = ObjectUtils.tryCast(descriptor.getPsiElement(), PsiMethodCallExpression.class);
      if (call == null) return;
      final PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
      if (qualifier == null) return;
      PsiExpression strippedQualifier = PsiUtil.deparenthesizeExpression(qualifier);
      if (strippedQualifier == null) return;
      final CommentTracker ct = new CommentTracker();
      PsiElement result = ct.replace(qualifier, "new java.util.HashSet<>(" + ct.text(strippedQualifier) + ")");
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(result);
    }
  }
}
