// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.redundancy;

import com.intellij.codeInspection.*;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.util.ObjectUtils.tryCast;

public final class RedundantClassCallInspection extends AbstractBaseJavaLocalInspectionTool implements CleanupLocalInspectionTool {
  private static final CallMatcher IS_INSTANCE =
    CallMatcher.exactInstanceCall(CommonClassNames.JAVA_LANG_CLASS, "isInstance").parameterCount(1);
  private static final CallMatcher CAST =
    CallMatcher.exactInstanceCall(CommonClassNames.JAVA_LANG_CLASS, "cast").parameterCount(1);

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
        PsiElement nameElement = call.getMethodExpression().getReferenceNameElement();
        if (nameElement == null) return;
        boolean isInstance = IS_INSTANCE.test(call);
        boolean cast = CAST.test(call);
        if (isInstance || cast) {
          PsiExpression qualifier = PsiUtil.skipParenthesizedExprDown(call.getMethodExpression().getQualifierExpression());
          if (qualifier instanceof PsiClassObjectAccessExpression) {
            PsiTypeElement typeElement = ((PsiClassObjectAccessExpression)qualifier).getOperand();
            PsiType classType = typeElement.getType();
            PsiExpression argument = call.getArgumentList().getExpressions()[0];
            PsiType argumentType = argument.getType();
            if (argumentType == null ||
                (isInstance && TypeConversionUtil.isPrimitiveAndNotNull(argumentType)) ||
                !argumentType.isConvertibleFrom(classType)) {
              // will be a compilation error after replacement; skip this
              return;
            }
            LocalQuickFix fix = isInstance ? new ReplaceWithInstanceOfFix(typeElement) : new ReplaceWithCastFix(typeElement);
            holder.registerProblem(nameElement, InspectionGadgetsBundle.message("redundant.call.problem.descriptor"), fix);
          }
        }
      }
    };
  }

  private abstract static class ReplaceRedundantClassCallFix extends PsiUpdateModCommandQuickFix {
    final String myReplacement;

    ReplaceRedundantClassCallFix(@NonNls String replacement) {
      myReplacement = replacement;
    }

    @Override
    public @NotNull String getName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", myReplacement);
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
      if (call == null) return;
      PsiExpression arg = ArrayUtil.getFirstElement(call.getArgumentList().getExpressions());
      if (arg == null) return;
      PsiClassObjectAccessExpression qualifier =
        tryCast(PsiUtil.skipParenthesizedExprDown(call.getMethodExpression().getQualifierExpression()),
                PsiClassObjectAccessExpression.class);
      if (qualifier == null) return;
      CommentTracker ct = new CommentTracker();
      ct.replaceAndRestoreComments(call, createReplacement(ct.text(arg), ct.text(qualifier.getOperand())));
    }

    abstract @NotNull String createReplacement(String argText, String classText);
  }

  private static class ReplaceWithInstanceOfFix extends ReplaceRedundantClassCallFix {
    ReplaceWithInstanceOfFix(@NotNull PsiTypeElement typeElement) {
      super("instanceof "+typeElement.getType().getPresentableText());
    }

    @Override
    public @NotNull String getFamilyName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", JavaKeywords.INSTANCEOF);
    }

    @Override
    @NotNull @NonNls String createReplacement(String argText, String classText) {
      return argText + " instanceof " + classText;
    }
  }

  private static class ReplaceWithCastFix extends ReplaceRedundantClassCallFix {
    ReplaceWithCastFix(@NotNull PsiTypeElement typeElement) {
      super("("+typeElement.getType().getPresentableText()+")");
    }

    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("replace.with.cast.fix.family.name");
    }

    @NotNull
    @Override
    String createReplacement(String argText, String classText) {
      return "("+classText+")"+argText;
    }
  }
}
