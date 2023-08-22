// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.IndexedContainer;
import com.siyeh.ig.psiutils.MethodCallUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class SequencedCollectionMethodCanBeUsedInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final CallMatcher LIST_GET_REMOVE = CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_LIST, "get", "remove")
    .parameterTypes("int");
  private static final CallMatcher ITERATOR_NEXT = CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_ITERATOR, "next")
    .parameterCount(0);
  private static final CallMatcher COLLECTION_ITERATOR = CallMatcher.instanceCall("java.util.Collection", "iterator")
    .parameterCount(0);

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (PsiUtil.getLanguageLevel(holder.getFile()).isLessThan(LanguageLevel.JDK_21)) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
        if (LIST_GET_REMOVE.matches(call)) {
          processListGetRemove(call);
        }
        if (ITERATOR_NEXT.matches(call)) {
          processIteratorNext(call);
        }
      }

      private void processIteratorNext(@NotNull PsiMethodCallExpression call) {
        PsiMethodCallExpression qualifierCall = MethodCallUtils.getQualifierMethodCall(call);
        if (!COLLECTION_ITERATOR.matches(qualifierCall)) return;
        PsiExpression collection = qualifierCall.getMethodExpression().getQualifierExpression();
        if (collection == null || collection instanceof PsiThisExpression) return;
        if (!InheritanceUtil.isInheritor(collection.getType(), "java.util.SequencedCollection")) return;
        String name = "getFirst";
        report(call, name);
      }

      private void report(@NotNull PsiMethodCallExpression call, String name) {
        holder.registerProblem(
          Objects.requireNonNull(call.getMethodExpression().getReferenceNameElement()),
          JavaBundle.message("inspection.stream.api.migration.can.be.replaced.with.call", name + "()"),
          new ReplaceWithCallFix(name));
      }

      private void processListGetRemove(@NotNull PsiMethodCallExpression call) {
        PsiReferenceExpression methodExpr = call.getMethodExpression();
        String name = methodExpr.getReferenceName();
        PsiExpression list = PsiUtil.skipParenthesizedExprDown(methodExpr.getQualifierExpression());
        if (list == null || list instanceof PsiThisExpression) return;
        PsiExpression arg = PsiUtil.skipParenthesizedExprDown(call.getArgumentList().getExpressions()[0]);
        if (ExpressionUtils.isZero(arg)) {
          report(call, name + "First");
        }
        if (arg instanceof PsiBinaryExpression binOp && binOp.getOperationTokenType().equals(JavaTokenType.MINUS) &&
            ExpressionUtils.isOne(binOp.getROperand())) {
          IndexedContainer container = IndexedContainer.fromLengthExpression(binOp.getLOperand());
          if (container != null && container.isQualifierEquivalent(list)) {
            report(call, name + "Last");
          }
        }
      }
    };
  }

  private static class ReplaceWithCallFix extends PsiUpdateModCommandQuickFix {
    private final String myName;

    private ReplaceWithCallFix(String methodName) {
      myName = methodName;
    }

    @Override
    public @NotNull String getName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", myName + "()");
    }

    @Override
    public @NotNull String getFamilyName() {
      return JavaBundle.message("intention.sequenced.collection.can.be.used.fix.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
      if (call == null) return;
      CommentTracker ct = new CommentTracker();
      ct.delete(call.getArgumentList().getExpressions());
      ExpressionUtils.bindCallTo(call, myName);
      PsiMethodCallExpression qualifier = MethodCallUtils.getQualifierMethodCall(call);
      if (qualifier != null && InheritanceUtil.isInheritor(qualifier.getType(), CommonClassNames.JAVA_UTIL_ITERATOR)) {
        PsiExpression collection = qualifier.getMethodExpression().getQualifierExpression();
        if (collection != null) {
          ct.replace(qualifier, collection);
        }
      }
      ct.insertCommentsBefore(call);
    }
  }
}
