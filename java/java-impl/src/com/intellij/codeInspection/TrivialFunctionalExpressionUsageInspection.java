// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.CommonJavaInlineUtil;
import com.intellij.refactoring.util.LambdaRefactoringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

public final class TrivialFunctionalExpressionUsageInspection extends AbstractBaseJavaLocalInspectionTool {
  @Override
  public @NotNull PsiElementVisitor buildVisitor(final @NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {

      @Override
      public void visitMethodReferenceExpression(final @NotNull PsiMethodReferenceExpression expression) {
        Problem problem = doCheckMethodCallOnFunctionalExpression(expression, call -> expression.resolve() != null);
        if (problem != null) {
          problem.register(holder);
        }
      }

      @Override
      public void visitLambdaExpression(final @NotNull PsiLambdaExpression expression) {
        Problem problem = doCheckLambda(expression);
        if (problem != null) {
          problem.register(holder);
        }
      }

      @Override
      public void visitAnonymousClass(final @NotNull PsiAnonymousClass aClass) {
        if (AnonymousCanBeLambdaInspection.canBeConvertedToLambda(aClass, false, Collections.emptySet())) {
          final PsiNewExpression newExpression = ObjectUtils.tryCast(aClass.getParent(), PsiNewExpression.class);
          Problem problem = doCheckMethodCallOnFunctionalExpression(call -> {
            final PsiMethod method = aClass.getMethods()[0];
            final PsiCodeBlock body = method.getBody();
            final PsiReturnStatement[] returnStatements = PsiUtil.findReturnStatements(body);
            if (returnStatements.length > 1) {
              return false;
            }
            final PsiElement callParent = PsiUtil.skipParenthesizedExprUp(call.getParent());
            return callParent instanceof PsiStatement ||
                   callParent instanceof PsiLocalVariable;
          }, newExpression, aClass.getBaseClassType(), new ReplaceAnonymousWithLambdaBodyFix());
          if (problem != null) {
            problem.register(holder);
          }
        }
      }
    };
  }

  public static void simplifyAllLambdas(@NotNull PsiElement context) {
    List<@NotNull Problem> problems = SyntaxTraverser.psiTraverser(context)
      .filter(PsiLambdaExpression.class)
      .filterMap(TrivialFunctionalExpressionUsageInspection::doCheckLambda)
      .toList();
    for (Problem problem : problems) {
      if (!problem.place().isValid()) continue;
      problem.fix().apply(problem.place());
    }
  }

  private static @Nullable Problem doCheckLambda(@NotNull PsiLambdaExpression expression) {
    final PsiElement body = expression.getBody();
    if (body == null) return null;

    Predicate<PsiMethodCallExpression> checkBody = call -> {
      final PsiElement callParent = call.getParent();

      if (!(body instanceof PsiCodeBlock)) {
        return callParent instanceof PsiStatement || callParent instanceof PsiLocalVariable || expression.isValueCompatible();
      }

      if (callParent instanceof PsiReturnStatement) {
        return true;
      }

      PsiStatement[] statements = ((PsiCodeBlock)body).getStatements();
      if (statements.length == 1) {
        return statements[0] instanceof PsiReturnStatement && expression.isValueCompatible();
      }

      final PsiReturnStatement[] returnStatements = PsiUtil.findReturnStatements((PsiCodeBlock)body);
      if (returnStatements.length > 1) {
        return false;
      }

      if (returnStatements.length == 1) {
        if (!(ArrayUtil.getLastElement(statements) instanceof PsiReturnStatement)) {
          return false;
        }
      }

      return CodeBlockSurrounder.canSurround(call);
    };
    Predicate<PsiMethodCallExpression> checkWrites = call ->
      !ContainerUtil.exists(expression.getParameterList().getParameters(), parameter -> VariableAccessUtils.variableIsAssigned(parameter, body));

    return doCheckMethodCallOnFunctionalExpression(expression, checkBody.and(checkWrites));
  }

  private static @Nullable Problem doCheckMethodCallOnFunctionalExpression(@NotNull PsiElement expression,
                                                                           @NotNull Predicate<? super PsiMethodCallExpression> elementContainerPredicate) {
    if (!(PsiUtil.skipParenthesizedExprUp(expression.getParent()) instanceof PsiTypeCastExpression parent)) return null;
    final PsiType interfaceType = parent.getType();
    return doCheckMethodCallOnFunctionalExpression(elementContainerPredicate, parent, interfaceType,
                                                   expression instanceof PsiLambdaExpression ? new ReplaceWithLambdaBodyFix()
                                                                                             : new ReplaceWithMethodReferenceFix());
  }

  private static @Nullable Problem doCheckMethodCallOnFunctionalExpression(@NotNull Predicate<? super PsiMethodCallExpression> elementContainerPredicate,
                                                                           PsiExpression qualifier,
                                                                           PsiType interfaceType,
                                                                           @NotNull ReplaceFix fix) {
    final PsiMethodCallExpression call = ExpressionUtils.getCallForQualifier(qualifier);
    if (call == null) return null;

    final PsiMethod method = call.resolveMethod();
    final PsiElement referenceNameElement = call.getMethodExpression().getReferenceNameElement();
    boolean suitableMethod = method != null &&
                             referenceNameElement != null &&
                             !method.isVarArgs() &&
                             call.getArgumentList().getExpressionCount() == method.getParameterList().getParametersCount() &&
                             elementContainerPredicate.test(call);
    if (!suitableMethod) return null;
    final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(interfaceType);
    if (method == interfaceMethod || interfaceMethod != null && MethodSignatureUtil.isSuperMethod(interfaceMethod, method)) {
      return new Problem(referenceNameElement, fix);
    }
    return null;
  }

  private record Problem(@NotNull PsiElement place, @NotNull ReplaceFix fix) {
    void register(@NotNull ProblemsHolder holder) {
      holder.registerProblem(place, InspectionGadgetsBundle.message("inspection.trivial.functional.expression.usage.description"), fix);
    }
  }

  private static void replaceWithLambdaBody(PsiLambdaExpression lambda) {
    lambda = extractSideEffects(lambda);
    if (lambda == null) return;
    PsiMethodCallExpression callExpression = PsiTreeUtil.getParentOfType(lambda, PsiMethodCallExpression.class);
    if (callExpression == null) return;
    PsiElement body = lambda.getBody();
    PsiExpression expression = LambdaUtil.extractSingleExpressionFromBody(body);
    if (expression != null) {
      replaceExpression(callExpression, lambda);
    }
    else if (body instanceof PsiCodeBlock) {
      replaceCodeBlock(lambda);
    }
  }

  private static PsiLambdaExpression extractSideEffects(PsiLambdaExpression lambda) {
    PsiMethodCallExpression callExpression = PsiTreeUtil.getParentOfType(lambda, PsiMethodCallExpression.class);
    if (callExpression == null) return lambda;
    PsiExpression[] arguments = callExpression.getArgumentList().getExpressions();
    if (!ContainerUtil.exists(arguments, SideEffectChecker::mayHaveSideEffects)) return lambda;

    CodeBlockSurrounder surrounder = CodeBlockSurrounder.forExpression(lambda);
    if (surrounder == null) return null;
    lambda = (PsiLambdaExpression)surrounder.surround().getExpression();
    callExpression = PsiTreeUtil.getParentOfType(lambda, PsiMethodCallExpression.class);
    if (callExpression == null) return lambda;
    arguments = callExpression.getArgumentList().getExpressions();
    PsiParameter[] parameters = lambda.getParameterList().getParameters();
    if (arguments.length != parameters.length) return lambda;
    PsiStatement anchor = PsiTreeUtil.getParentOfType(lambda, PsiStatement.class, false);
    if (anchor == null) return lambda;

    List<PsiStatement> statements = new ArrayList<>();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(lambda.getProject());
    List<List<PsiExpression>> allSideEffects = ContainerUtil.map(arguments, SideEffectChecker::extractSideEffectExpressions);
    int lastSideEffectIndex = ContainerUtil.lastIndexOf(allSideEffects, se -> !se.isEmpty());

    for (int i = 0; i < arguments.length; i++) {
      PsiExpression argument = arguments[i];
      PsiParameter parameter = parameters[i];
      List<PsiExpression> sideEffects = SideEffectChecker.extractSideEffectExpressions(argument);
      if (i <= lastSideEffectIndex) {
        boolean used = VariableAccessUtils.variableIsUsed(parameter, lambda.getBody());
        if (used) {
          String name = parameter.getName();
          statements.add(
            factory.createStatementFromText(parameter.getType().getCanonicalText() + " " + name + "=" + argument.getText()+";", lambda));
          argument.replace(factory.createExpressionFromText(name, parameter));
        }
        else {
          Collections.addAll(statements, StatementExtractor.generateStatements(sideEffects, argument));
        }
      }
    }
    BlockUtils.addBefore(anchor, statements.toArray(PsiStatement.EMPTY_ARRAY));
    return lambda;
  }

  private static void replaceExpression(PsiMethodCallExpression callExpression, PsiLambdaExpression element) {
    PsiExpression expression;
    final CommentTracker ct = new CommentTracker();
    inlineCallArguments(callExpression, element, ct);
    // body could be invalidated after inlining
    expression = LambdaUtil.extractSingleExpressionFromBody(element.getBody());
    ct.replaceAndRestoreComments(callExpression, expression);
  }

  private static void replaceCodeBlock(PsiLambdaExpression element) {
    CodeBlockSurrounder surrounder = CodeBlockSurrounder.forExpression(element);
    if (surrounder == null) return;
    CodeBlockSurrounder.SurroundResult result = surrounder.surround();
    element = (PsiLambdaExpression)result.getExpression();
    PsiElement body = element.getBody();
    if (!(body instanceof PsiCodeBlock)) return;
    PsiMethodCallExpression callExpression = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
    if (callExpression == null) return;
    final CommentTracker ct = new CommentTracker();
    inlineCallArguments(callExpression, element, ct);
    body = element.getBody();
    final PsiElement parent = callExpression.getParent();
    final PsiStatement[] statements = ((PsiCodeBlock)body).getStatements();
    PsiReturnStatement statement = null;
    if (statements.length > 0) {
      PsiElement anchor = result.getAnchor();
      statement = ObjectUtils.tryCast(statements[statements.length - 1], PsiReturnStatement.class);
      PsiElement gParent = anchor.getParent();
      solveNameConflicts(statements, anchor, element);
      for (PsiElement child = body.getFirstChild(); child != null; child = child.getNextSibling()) {
        if (child != statement && !(child instanceof PsiJavaToken)) {
          gParent.addBefore(ct.markUnchanged(child), anchor);
        }
      }
    }
    final PsiExpression returnValue = statement == null ? null : statement.getReturnValue();
    if (returnValue != null) {
      ct.replaceAndRestoreComments(callExpression, returnValue);
    }
    else {
      if (parent instanceof PsiExpressionStatement) {
        ct.deleteAndRestoreComments(callExpression);
      }
      else {
        ct.deleteAndRestoreComments(parent);
      }
    }
  }

  private static void solveNameConflicts(PsiStatement[] statements, @NotNull PsiElement anchor, @NotNull PsiLambdaExpression lambda) {
    Predicate<PsiVariable> allowedVar = variable -> PsiTreeUtil.isAncestor(lambda, variable, true);
    Project project = anchor.getProject();
    JavaCodeStyleManager manager = JavaCodeStyleManager.getInstance(project);
    StreamEx.of(statements).select(PsiDeclarationStatement.class)
      .flatArray(PsiDeclarationStatement::getDeclaredElements)
      .select(PsiVariable.class)
      .forEach(e -> {
        PsiIdentifier identifier = e.getNameIdentifier();
        if (identifier == null) return;
        String name = identifier.getText();
        String newName = manager.suggestUniqueVariableName(name, anchor, allowedVar);
        if (!name.equals(newName)) {
          List<PsiReferenceExpression> refs = VariableAccessUtils.getVariableReferences(e, anchor);
          refs.forEach(ref -> ref.handleElementRename(newName));
          identifier.replace(JavaPsiFacade.getElementFactory(project).createIdentifier(newName));
        }
      });
  }

  private static void inlineCallArguments(PsiMethodCallExpression callExpression,
                                          PsiLambdaExpression element,
                                          CommentTracker ct) {
    final PsiExpression[] args = callExpression.getArgumentList().getExpressions();
    final PsiParameter[] parameters = element.getParameterList().getParameters();
    for (int i = 0; i < parameters.length; i++) {
      final PsiParameter parameter = parameters[i];
      final PsiExpression initializer = args[i];
      for (PsiReferenceExpression referenceElement : VariableAccessUtils.getVariableReferences(parameter)) {
        ct.markUnchanged(initializer);
        CommonJavaInlineUtil.getInstance().inlineVariable(parameter, initializer, referenceElement, null);
      }
    }
  }

  private static class ReplaceWithLambdaBodyFix extends ReplaceFix {

    @Override
    public @Nls @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("replace.with.lambda.body.fix.family.name");
    }

    @Override
    protected void fixExpression(PsiMethodCallExpression callExpression, PsiExpression qualifierExpression) {
      if (qualifierExpression instanceof PsiTypeCastExpression) {
        final PsiExpression element = PsiUtil.skipParenthesizedExprDown(((PsiTypeCastExpression)qualifierExpression).getOperand());
        if (element instanceof PsiLambdaExpression) {
          replaceWithLambdaBody((PsiLambdaExpression)element);
        }
      }
    }
  }

  private static class ReplaceWithMethodReferenceFix extends ReplaceFix {
    @Override
    public @Nls @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("replace.with.method.reference.fix.family.name");
    }

    @Override
    protected void fixExpression(PsiMethodCallExpression callExpression, PsiExpression qualifierExpression) {
      if (qualifierExpression instanceof PsiTypeCastExpression) {
        final PsiExpression element = ((PsiTypeCastExpression)qualifierExpression).getOperand();
        if (element instanceof PsiMethodReferenceExpression) {
          final PsiLambdaExpression lambdaExpression =
            LambdaRefactoringUtil.convertMethodReferenceToLambda((PsiMethodReferenceExpression)element, false, true);
          if (lambdaExpression != null) {
            replaceWithLambdaBody(lambdaExpression);
          }
        }
      }
    }
  }

  private static class ReplaceAnonymousWithLambdaBodyFix extends ReplaceFix {

    @Override
    public @Nls @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("replace.anonymous.with.lambda.body.fix.family.name");
    }

    @Override
    protected void fixExpression(PsiMethodCallExpression callExpression, PsiExpression qualifierExpression) {
      final PsiExpression cast = AnonymousCanBeLambdaInspection.replacePsiElementWithLambda(qualifierExpression, true, false);
      if (cast instanceof PsiTypeCastExpression) {
        final PsiExpression lambdaExpression = ((PsiTypeCastExpression)cast).getOperand();
        if (lambdaExpression instanceof PsiLambdaExpression) {
          replaceWithLambdaBody((PsiLambdaExpression)lambdaExpression);
        }
      }
    }
  }

  private abstract static class ReplaceFix extends PsiUpdateModCommandQuickFix {
    @Override
    public @Nls @NotNull String getName() {
      return getFamilyName();
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      apply(element);
    }

    void apply(@NotNull PsiElement psiElement) {
      final PsiMethodCallExpression callExpression = PsiTreeUtil.getParentOfType(psiElement, PsiMethodCallExpression.class);
      if (callExpression != null) {
        fixExpression(callExpression, PsiUtil.skipParenthesizedExprDown(callExpression.getMethodExpression().getQualifierExpression()));
      }
    }

    protected abstract void fixExpression(PsiMethodCallExpression callExpression, PsiExpression qualifierExpression);
  }
}
