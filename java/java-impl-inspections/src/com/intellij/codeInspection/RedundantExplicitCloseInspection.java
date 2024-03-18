// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.impl.quickfix.DeleteElementFix;
import com.intellij.java.JavaBundle;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.intellij.util.ObjectUtils.tryCast;

public final class RedundantExplicitCloseInspection extends AbstractBaseJavaLocalInspectionTool {
  CallMatcher CLOSE = CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_AUTO_CLOSEABLE, "close").parameterCount(0);

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitTryStatement(@NotNull PsiTryStatement statement) {
        PsiResourceList resourceList = statement.getResourceList();
        if (resourceList == null) return;

        PsiCodeBlock tryBlock = statement.getTryBlock();
        if (tryBlock == null) return;
        List<PsiStatement> terminatingStatements = getTerminatingStatements(ArrayUtil.getLastElement(tryBlock.getStatements()));
        for (PsiStatement last : terminatingStatements) {
          PsiExpressionStatement expressionStatement = tryCast(last, PsiExpressionStatement.class);
          if(expressionStatement == null) return;
          PsiMethodCallExpression call = tryCast(expressionStatement.getExpression(), PsiMethodCallExpression.class);
          if (!CLOSE.test(call)) return;
          PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
          PsiReferenceExpression reference = tryCast(PsiUtil.skipParenthesizedExprDown(qualifier), PsiReferenceExpression.class);
          if(reference == null) return;
          PsiVariable variable = tryCast(reference.resolve(), PsiVariable.class);
          if(variable == null) return;
          boolean isReferenceToResourceVariable = StreamEx.of(resourceList.iterator()).anyMatch(
            element -> variable == element ||
                       element instanceof PsiResourceExpression &&
                       EquivalenceChecker.getCanonicalPsiEquivalence()
                         .expressionsAreEquivalent(reference, ((PsiResourceExpression)element).getExpression()));
          if(!isReferenceToResourceVariable) return;
          holder.problem(last, JavaBundle.message("inspection.redundant.explicit.close"))
            .fix(new DeleteElementFix(last, CommonQuickFixBundle.message("fix.remove.redundant", "close()")))
            .register();
        }
      }
    };
  }

  @NotNull
  private static List<PsiStatement> getTerminatingStatements(@Nullable PsiStatement last) {
    if (last == null) return Collections.emptyList();
    List<PsiStatement> terminatingStatements = new ArrayList<>();
    PsiIfStatement ifStatement = tryCast(last, PsiIfStatement.class);
    if (ifStatement != null) {
      PsiStatement[] thenStatements = ControlFlowUtils.unwrapBlock(ifStatement.getThenBranch());
      terminatingStatements.addAll(getTerminatingStatements(ArrayUtil.getLastElement(thenStatements)));
      PsiStatement elseBranch = ifStatement.getElseBranch();
      if (elseBranch != null) {
        PsiStatement[] elseStatements = ControlFlowUtils.unwrapBlock(elseBranch);
        terminatingStatements.addAll(getTerminatingStatements(ArrayUtil.getLastElement(elseStatements)));
      }
    } else {
      terminatingStatements.add(last);
    }
    return terminatingStatements;
  }
}
