// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.Mutability;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.CommonClassNames.JAVA_UTIL_COLLECTIONS;
import static com.siyeh.ig.callMatcher.CallMatcher.anyOf;
import static com.siyeh.ig.callMatcher.CallMatcher.staticCall;

public class RedundantUnmodifiableInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final CallMatcher COLLECTIONS_UNMODIFIABLE_COLLECTION =
    staticCall(JAVA_UTIL_COLLECTIONS, "unmodifiableCollection").parameterCount(1);
  private static final CallMatcher COLLECTIONS_UNMODIFIABLE_LIST =
    staticCall(JAVA_UTIL_COLLECTIONS, "unmodifiableList").parameterCount(1);
  private static final CallMatcher COLLECTIONS_UNMODIFIABLE_SET =
    staticCall(JAVA_UTIL_COLLECTIONS, "unmodifiableSet").parameterCount(1);
  private static final CallMatcher COLLECTIONS_UNMODIFIABLE_MAP =
    staticCall(JAVA_UTIL_COLLECTIONS, "unmodifiableMap").parameterCount(1);

  private static final CallMatcher COLLECTIONS_UNMODIFIABLE_SORTED_SET =
    staticCall(JAVA_UTIL_COLLECTIONS, "unmodifiableSortedSet").parameterCount(1);
  private static final CallMatcher COLLECTIONS_UNMODIFIABLE_SORTED_MAP =
    staticCall(JAVA_UTIL_COLLECTIONS, "unmodifiableSortedMap").parameterCount(1);

  private static final CallMatcher COLLECTIONS_UNMODIFIABLE_NAVIGABLE_MAP =
    staticCall(JAVA_UTIL_COLLECTIONS, "unmodifiableNavigableMap").parameterCount(1);
  private static final CallMatcher COLLECTIONS_UNMODIFIABLE_NAVIGABLE_SET =
    staticCall(JAVA_UTIL_COLLECTIONS, "unmodifiableNavigableSet").parameterCount(1);

  private static final CallMatcher COLLECTIONS_UNMODIFIABLE =
    anyOf(COLLECTIONS_UNMODIFIABLE_COLLECTION, COLLECTIONS_UNMODIFIABLE_SET,
          COLLECTIONS_UNMODIFIABLE_MAP, COLLECTIONS_UNMODIFIABLE_LIST,
          COLLECTIONS_UNMODIFIABLE_SORTED_SET, COLLECTIONS_UNMODIFIABLE_SORTED_MAP,
          COLLECTIONS_UNMODIFIABLE_NAVIGABLE_MAP, COLLECTIONS_UNMODIFIABLE_NAVIGABLE_SET);

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {

      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression call) {
        super.visitMethodCallExpression(call);

        if (COLLECTIONS_UNMODIFIABLE.test(call)) {
          PsiExpression arg = ArrayUtil.getFirstElement(call.getArgumentList().getExpressions());
          if (arg == null) return;

          DfType dfType = CommonDataflow.getDfType(arg);
          if (!Mutability.fromDfType(dfType).isUnmodifiable()) return;

          String methodName = call.getMethodExpression().getReferenceName();
          if (methodName == null) return;

          holder.registerProblem(call,
                                 JavaBundle.message("inspection.redundant.unmodifiable.call.display.name", methodName),
                                 ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                                 new TextRange(0, call.getArgumentList().getStartOffsetInParent()),
                                 new UnwrapUnmodifiableFix());
        }
      }
    };
  }

  private static class UnwrapUnmodifiableFix implements LocalQuickFix {
    @NotNull
    @Override
    public String getFamilyName() {
      return JavaBundle.message("inspection.redundant.unmodifiable.call.replace.with.arg.quickfix");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      PsiMethodCallExpression call = ObjectUtils.tryCast(element, PsiMethodCallExpression.class);
      if (call == null) return;

      final PsiExpressionList argList = call.getArgumentList();
      final PsiExpression[] args = argList.getExpressions();
      if (args.length != 1) return;

      CommentTracker commentTracker = new CommentTracker();
      commentTracker.replaceAndRestoreComments(element, commentTracker.text(args[0]));
    }
  }
}
