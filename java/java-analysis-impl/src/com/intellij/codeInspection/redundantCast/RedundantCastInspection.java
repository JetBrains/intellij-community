// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.redundantCast;

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.miscGenerics.SuspiciousMethodCallUtil;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiExpressionTrimRenderer;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.RedundantCastUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.bugs.PrimitiveArrayArgumentToVariableArgMethodInspection;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.format.FormatDecode;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.VariableNameGenerator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class RedundantCastInspection extends AbstractBaseJavaLocalInspectionTool implements CleanupLocalInspectionTool {
  private final LocalQuickFix myQuickFixAction;
  
  private static final CallMatcher CLASS_CAST_MATCHER = CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_CLASS, "cast")
    .parameterCount(1);
  
  private static final @NonNls String SHORT_NAME = "RedundantCast";

  public boolean IGNORE_SUSPICIOUS_METHOD_CALLS = true;

  public RedundantCastInspection() {
    myQuickFixAction = new AcceptSuggested();
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel5OrHigher(holder.getFile())) return PsiElementVisitor.EMPTY_VISITOR;

    return new RedundantCastUtil.RedundantCastVisitorBase() {
      @Override
      public void visitMethodReferenceExpression(@NotNull PsiMethodReferenceExpression methodRef) {
        super.visitMethodReferenceExpression(methodRef);
        if (CLASS_CAST_MATCHER.methodReferenceMatches(methodRef)) {
          PsiType type = methodRef.getFunctionalInterfaceType();
          if (type == null) return;
          PsiType parameterType = LambdaUtil.getLambdaParameterFromType(type, 0);
          PsiType returnType = LambdaUtil.getFunctionalInterfaceReturnType(type);
          if (parameterType != null && parameterType.equals(returnType)) {
            holder.problem(methodRef, JavaAnalysisBundle.message("inspection.redundant.cast.methodref.descriptor"))
              .fix(new ReplaceWithIdentityLambdaFix()).register();
          }
        }
      }

      @Override
      protected void registerCast(@NotNull PsiTypeCastExpression typeCast) {
        ProblemDescriptor descriptor = createDescription(typeCast, holder.getManager(), isOnTheFly);
        if (descriptor != null) {
          holder.registerProblem(descriptor);
        }
      }
    };
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("IGNORE_SUSPICIOUS_METHOD_CALLS", JavaAnalysisBundle.message("ignore.casts.in.suspicious.collections.method.calls")));
  }

  private @Nullable ProblemDescriptor createDescription(@NotNull PsiTypeCastExpression cast, @NotNull InspectionManager manager, boolean onTheFly) {
    PsiExpression operand = cast.getOperand();
    PsiTypeElement castType = cast.getCastType();
    if (operand == null || castType == null) return null;
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(cast.getParent());
    if (parent instanceof PsiExpressionList)  {
      final PsiElement gParent = parent.getParent();
      if (gParent instanceof PsiMethodCallExpression call && IGNORE_SUSPICIOUS_METHOD_CALLS) {
        PsiType operandType = operand.getType();
        final String message =
          SuspiciousMethodCallUtil.getSuspiciousMethodCallMessage(call, operand, operandType, true, new ArrayList<>(), 0);
        if (message != null) {
          return null;
        }
        PsiExpression[] arguments = call.getArgumentList().getExpressions();
        if (arguments.length > 0) {
          PsiExpression lastArgument = arguments[arguments.length - 1];
          if (lastArgument == cast
              && PrimitiveArrayArgumentToVariableArgMethodInspection.isConfusingArgument(call, operand, arguments)) {
            return null;
          }
        }
        if (FormatDecode.isSuspiciousFormatCall(call, cast)) {
          return null;
        }
        
      }
    }

    String message = JavaAnalysisBundle.message("inspection.redundant.cast.problem.descriptor", PsiExpressionTrimRenderer.render(operand));
    return manager.createProblemDescriptor(castType, message, myQuickFixAction, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, onTheFly);
  }


  private static class AcceptSuggested extends PsiUpdateModCommandQuickFix {
    @Override
    public @NotNull String getFamilyName() {
      return JavaAnalysisBundle.message("inspection.redundant.cast.remove.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement castTypeElement, @NotNull ModPsiUpdater updater) {
      if (castTypeElement.getParent() instanceof PsiTypeCastExpression cast) {
        RemoveRedundantCastUtil.removeCast(cast);
      }
    }
  }

  @Override
  public @NotNull String getGroupDisplayName() {
    return InspectionsBundle.message("group.names.verbose.or.redundant.code.constructs");
  }

  @Override
  public @NotNull String getShortName() {
    return SHORT_NAME;
  }
  
  private static class ReplaceWithIdentityLambdaFix extends PsiUpdateModCommandQuickFix {
    @Override
    public @NotNull String getFamilyName() {
      return JavaAnalysisBundle.message("inspection.redundant.cast.remove.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiMethodReferenceExpression methodRef = ObjectUtils.tryCast(element, PsiMethodReferenceExpression.class);
      if (methodRef == null) return;
      VariableNameGenerator generator = new VariableNameGenerator(element, VariableKind.PARAMETER);
      PsiType type = methodRef.getFunctionalInterfaceType();
      PsiMethod method = LambdaUtil.getFunctionalInterfaceMethod(type);
      PsiType returnType = LambdaUtil.getFunctionalInterfaceReturnType(type);
      if (returnType != null) {
        generator.byType(returnType);
      }
      if (method != null) {
        PsiParameter parameter = method.getParameterList().getParameter(0);
        if (parameter != null) {
          generator.byName(parameter.getName());
        }
      }
      String varName = generator.generate(true);
      new CommentTracker().replaceAndRestoreComments(element, varName + "->" + varName);
    }
  }
}
