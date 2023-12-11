// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInspection.dataFlow.DfaUtil;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.util.OptionalUtil;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class OptionalAssignedToNullInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final CallMatcher MAP_GET = CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_MAP, "get").parameterTypes(
    CommonClassNames.JAVA_LANG_OBJECT);

  public boolean WARN_ON_COMPARISON = true;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("WARN_ON_COMPARISON", JavaBundle.message("inspection.null.value.for.optional.option.comparisons")));
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
        checkNulls(expression.getType(), expression.getRExpression(),
                   JavaBundle.message("inspection.null.value.for.optional.context.assignment"));
      }

      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
        PsiExpression[] args = call.getArgumentList().getExpressions();
        if (args.length == 0) return;
        PsiMethod method = call.resolveMethod();
        if (method == null) return;
        PsiParameter[] parameters = method.getParameterList().getParameters();
        if (parameters.length > args.length) return;
        boolean varArgCall = method.isVarArgs() && MethodCallUtils.isVarArgCall(call);
        if (!varArgCall && parameters.length < args.length) return;
        for (int i = 0; i < args.length; i++) {
          PsiParameter parameter = parameters[Math.min(parameters.length - 1, i)];
          PsiType type = parameter.getType();
          if (varArgCall && i >= parameters.length - 1 && type instanceof PsiEllipsisType) {
            type = ((PsiEllipsisType)type).getComponentType();
          }
          checkNulls(type, args[i], JavaBundle.message("inspection.null.value.for.optional.context.parameter"));
        }
      }

      @Override
      public void visitLambdaExpression(@NotNull PsiLambdaExpression lambda) {
        PsiElement body = lambda.getBody();
        if (body instanceof PsiExpression) {
          checkNulls(LambdaUtil.getFunctionalInterfaceReturnType(lambda), (PsiExpression)body,
                     JavaBundle.message("inspection.null.value.for.optional.context.lambda"));
        }
      }

      @Override
      public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
        checkNulls(PsiTypesUtil.getMethodReturnType(statement), statement.getReturnValue(),
                   JavaBundle.message("inspection.null.value.for.optional.context.return"));
      }

      @Override
      public void visitVariable(@NotNull PsiVariable variable) {
        checkNulls(variable.getType(), variable.getInitializer(),
                   JavaBundle.message("inspection.null.value.for.optional.context.declaration"));
      }

      @Override
      public void visitBinaryExpression(@NotNull PsiBinaryExpression binOp) {
        if (!WARN_ON_COMPARISON) return;
        PsiExpression value = ExpressionUtils.getValueComparedWithNull(binOp);
        if (value != null &&
            TypeUtils.isOptional(value.getType()) &&
            !hasSubsequentIsPresentCall(value, binOp, JavaTokenType.EQEQ.equals(binOp.getOperationTokenType())) &&
            !comesFromMapGet(value)) {
          boolean useIsEmpty =
            binOp.getOperationTokenType().equals(JavaTokenType.EQEQ) &&
            PsiUtil.isLanguageLevel11OrHigher(binOp);
          holder.problem(binOp, JavaBundle.message("inspection.null.value.for.optional.assigned.message"))
            .fix(new ReplaceWithIsPresentFix(useIsEmpty))
            .fix(new UpdateInspectionOptionFix(OptionalAssignedToNullInspection.this, "WARN_ON_COMPARISON",
                                               JavaBundle.message("inspection.null.value.for.optional.assigned.ignore.fix.name"), false))
            .register();
        }
      }

      private static boolean comesFromMapGet(PsiExpression value) {
        PsiLocalVariable local = ExpressionUtils.resolveLocalVariable(value);
        if (local != null) {
          PsiExpression initializer = ContainerUtil.getOnlyItem(DfaUtil.getVariableValues(local, value));
          if (initializer != null) {
            value = initializer;
          }
        }
        return MAP_GET.matches(ExpressionUtils.resolveExpression(value));
      }

      private static boolean hasSubsequentIsPresentCall(@NotNull PsiExpression optionalExpression,
                                                        @NotNull PsiExpression previousExpression,
                                                        boolean negated) {
        PsiPolyadicExpression parent =
          ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprUp(previousExpression.getParent()), PsiPolyadicExpression.class);
        if (parent == null) return false;
        IElementType expectedToken = negated ? JavaTokenType.OROR : JavaTokenType.ANDAND;
        if (!parent.getOperationTokenType().equals(expectedToken)) return false;
        PsiExpression nextExpression =
          StreamEx.of(parent.getOperands()).dropWhile(op -> !PsiTreeUtil.isAncestor(op, previousExpression, false))
                  .skip(1)
                  .findFirst()
                  .orElse(null);
        nextExpression = PsiUtil.skipParenthesizedExprDown(nextExpression);
        if (nextExpression == null) return false;
        if (negated) {
          if (!BoolUtils.isNegation(nextExpression)) return false;
          nextExpression = BoolUtils.getNegated(nextExpression);
        }
        if (!(nextExpression instanceof PsiMethodCallExpression call)) return false;
        if (!"isPresent".equals(call.getMethodExpression().getReferenceName()) || !call.getArgumentList().isEmpty()) return false;
        PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
        return qualifier != null && PsiEquivalenceUtil.areElementsEquivalent(qualifier, optionalExpression);
      }

      private void checkNulls(PsiType type, PsiExpression expression, String declaration) {
        if (expression != null && TypeUtils.isOptional(type)) {
          ExpressionUtils.nonStructuralChildren(expression).filter(ExpressionUtils::isNullLiteral)
            .forEach(nullLiteral -> register(nullLiteral, (PsiClassType)type, declaration));
        }
      }

      private void register(PsiExpression expression, PsiClassType type, String contextName) {
        holder.registerProblem(expression,
                               JavaBundle.message("inspection.null.value.for.optional.message", contextName),
                               new ReplaceWithEmptyOptionalFix(type));
      }
    };
  }

  private static class ReplaceWithEmptyOptionalFix extends PsiUpdateModCommandQuickFix {
    private final String myTypeName;
    private final String myTypeParameter;
    private final String myMethodName;

    ReplaceWithEmptyOptionalFix(PsiClassType type) {
      myTypeName = type.rawType().getCanonicalText();
      PsiType[] parameters = type.getParameters();
      myTypeParameter =
        parameters.length == 1 ? "<" + GenericsUtil.getVariableTypeByExpressionType(parameters[0]).getCanonicalText() + ">" : "";
      myMethodName = myTypeName.equals(OptionalUtil.GUAVA_OPTIONAL) ? "absent" : "empty";
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return CommonQuickFixBundle.message("fix.replace.with.x",
                                       StringUtil.getShortName(myTypeName) + "." + myMethodName + "()");
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return JavaBundle.message("inspection.null.value.for.optional.fix.family.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (!(element instanceof PsiExpression)) return;
      String emptyCall = myTypeName + "." + myTypeParameter + myMethodName + "()";
      PsiElement result = new CommentTracker().replaceAndRestoreComments(element, emptyCall);
      RemoveRedundantTypeArgumentsUtil.removeRedundantTypeArguments(result);
    }
  }

  private static class ReplaceWithIsPresentFix extends PsiUpdateModCommandQuickFix {
    private final boolean myUseIsEmpty;

    private ReplaceWithIsPresentFix(boolean useIsEmpty) { myUseIsEmpty = useIsEmpty; }

    @Override
    public @NotNull String getName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", myUseIsEmpty ? "isEmpty" : "isPresent()");
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", "isPresent()");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiBinaryExpression binOp = ObjectUtils.tryCast(element, PsiBinaryExpression.class);
      if (binOp == null) return;
      PsiExpression value = ExpressionUtils.getValueComparedWithNull(binOp);
      if (value == null || !TypeUtils.isOptional(value.getType())) return;
      CommentTracker ct = new CommentTracker();
      String negation = myUseIsEmpty || binOp.getOperationTokenType().equals(JavaTokenType.NE) ? "" : "!";
      String methodName = myUseIsEmpty ? "isEmpty" : "isPresent";
      ct.replaceAndRestoreComments(binOp, negation + ct.text(value, ParenthesesUtils.METHOD_CALL_PRECEDENCE) + "." + methodName + "()");
    }
  }
}
