// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.TypeConstraint;
import com.intellij.codeInspection.dataFlow.jvm.SpecialField;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.types.DfIntegralType;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import static com.siyeh.ig.callMatcher.CallMatcher.instanceCall;

public final class SlowAbstractSetRemoveAllInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final String FOR_EACH_METHOD = "forEach";

  private static final CallMatcher SET_REMOVE_ALL =
    instanceCall(CommonClassNames.JAVA_UTIL_SET, "removeAll").parameterTypes(CommonClassNames.JAVA_UTIL_COLLECTION);

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {

      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
        super.visitMethodCallExpression(call);
        if (!SET_REMOVE_ALL.test(call)) return;
        final PsiExpression qualifier = ExpressionUtils.getEffectiveQualifier(call.getMethodExpression());
        if (qualifier == null) return;
        final PsiExpression arg = call.getArgumentList().getExpressions()[0];
        final TypeConstraint constraint = TypeConstraint.fromDfType(CommonDataflow.getDfType(arg));
        final PsiType type = constraint.getPsiType(holder.getProject());
        if (!InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_LIST)) return;
        final TypeConstraint qualifierConstraint = TypeConstraint.fromDfType(CommonDataflow.getDfType(qualifier));
        final PsiType qualifierType = qualifierConstraint.getPsiType(holder.getProject());
        if (InheritanceUtil.isInheritor(qualifierType, "java.util.concurrent.CopyOnWriteArraySet")) return;
        final LongRangeSet setSizeRange = getSizeRangeOfCollection(qualifier);
        if (setSizeRange.isEmpty() || setSizeRange.max() <= 1) return;
        final LongRangeSet listSizeRange = getSizeRangeOfCollection(arg);
        if (listSizeRange.isEmpty() || listSizeRange.max() <= 2) return;
        if (setSizeRange.min() > listSizeRange.max()) return;
        final LocalQuickFix[] fix;
        if (PsiUtil.isLanguageLevel8OrHigher(call) && ExpressionUtils.isVoidContext(call)) {
          final String replacement =
            ParenthesesUtils.getText(arg, ParenthesesUtils.POSTFIX_PRECEDENCE) + ".forEach(" + qualifier.getText() + "::remove)";
          fix = new LocalQuickFix[]{new ReplaceWithListForEachFix(replacement)};
        } else {
          fix = LocalQuickFix.EMPTY_ARRAY;
        }
        final PsiElement nameElement = call.getMethodExpression().getReferenceNameElement();
        if (nameElement == null) return;
        holder.registerProblem(call,
                               JavaBundle.message("inspection.slow.abstract.set.remove.all.description"),
                               ProblemHighlightType.WARNING,
                               nameElement.getTextRangeInParent(),
                               fix);
      }
    };
  }


  /**
   * Returns the set of possible values for the collection size
   *
   * @param collection a collection to get the range of possible values for its size
   * @return the set of possible values for the collection size
   */
  @NotNull
  public static LongRangeSet getSizeRangeOfCollection(PsiExpression collection) {
    final SpecialField lengthField = SpecialField.COLLECTION_SIZE;
    final DfType origType = CommonDataflow.getDfType(collection);
    final DfType length = lengthField.getFromQualifier(origType);
    final DfIntegralType dfType = ObjectUtils.tryCast(length, DfIntegralType.class);
    if (dfType == null) return LongRangeSet.all();
    return dfType.getRange();
  }

  private static class ReplaceWithListForEachFix extends PsiUpdateModCommandQuickFix {
    final String myExpressionText;

    ReplaceWithListForEachFix(String string) {
      myExpressionText = string;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", myExpressionText);
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return JavaBundle.message("inspection.slow.abstract.set.remove.all.fix.family.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final PsiMethodCallExpression call = ObjectUtils.tryCast(element, PsiMethodCallExpression.class);
      if (call == null) return;
      final PsiExpression[] args = call.getArgumentList().getExpressions();
      if (args.length != 1) return;
      final PsiExpression arg = args[0];
      final PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
      if (qualifier == null) return;
      ExpressionUtils.bindCallTo(call, FOR_EACH_METHOD);
      final CommentTracker ct = new CommentTracker();
      final String setRemove = qualifier.getText() + "::remove";
      ct.replace(qualifier, arg);
      ct.replace(arg, setRemove);
    }
  }
}
