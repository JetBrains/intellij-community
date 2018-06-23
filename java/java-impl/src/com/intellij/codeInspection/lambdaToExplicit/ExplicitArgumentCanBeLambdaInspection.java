// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.lambdaToExplicit;

import com.intellij.codeInspection.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.java.PsiEmptyExpressionImpl;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.apache.commons.lang.ArrayUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class ExplicitArgumentCanBeLambdaInspection extends AbstractBaseJavaLocalInspectionTool {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel8OrHigher(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression call) {
        for(LambdaAndExplicitMethodPair info : LambdaAndExplicitMethodPair.INFOS) {
          PsiExpression arg = info.getLambdaCandidateFromExplicitCall(call);
          if(arg != null && !(arg instanceof PsiEmptyExpressionImpl) && !ExpressionUtils.isSafelyRecomputableExpression(arg)) {
            holder.registerProblem(arg, "Explicit argument can be converted to lambda",
                                   new ConvertExplicitCallToLambdaFix(info, info.getLambdaMethodName(call)));
            return;
          }
        }
      }
    };
  }

  private static class ConvertExplicitCallToLambdaFix implements LocalQuickFix {
    private final LambdaAndExplicitMethodPair myInfo;
    private final String myName;

    public ConvertExplicitCallToLambdaFix(LambdaAndExplicitMethodPair info, String name) {
      myInfo = info;
      myName = name;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return InspectionsBundle.message("inspection.explicit.argument.can.be.lambda.fix.name", myName);
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionsBundle.message("inspection.explicit.argument.can.be.lambda.fix.family.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiExpression arg = ObjectUtils.tryCast(descriptor.getStartElement(), PsiExpression.class);
      if (arg == null) return;
      PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(arg, PsiMethodCallExpression.class);
      if (call == null) return;
      PsiExpression[] args = call.getArgumentList().getExpressions();
      int idx = ArrayUtils.indexOf(args, arg);
      if(idx < 0) return;
      ExpressionUtils.bindCallTo(call, myName);
      String lambdaText = myInfo.makeLambda(arg);
      PsiLambdaExpression lambda =
        (PsiLambdaExpression)arg.replace(JavaPsiFacade.getElementFactory(project).createExpressionFromText(lambdaText, arg));
      LambdaCanBeMethodReferenceInspection.replaceLambdaWithMethodReference(lambda);
    }
  }
}
