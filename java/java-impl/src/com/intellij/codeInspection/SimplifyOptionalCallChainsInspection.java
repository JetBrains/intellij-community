// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.DfaFactType;
import com.intellij.codeInspection.util.LambdaGenerationUtil;
import com.intellij.codeInspection.util.OptionalUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDiamondTypeUtil;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiExpressionTrimRenderer;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.LambdaRefactoringUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.regex.Pattern;

import static com.intellij.util.ObjectUtils.tryCast;


/**
 * @author Tagir Valeev
 */
public class SimplifyOptionalCallChainsInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final CallMatcher OPTIONAL_OR_ELSE =
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_OPTIONAL, "orElse").parameterCount(1);
  private static final CallMatcher OPTIONAL_GET =
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_OPTIONAL, "get").parameterCount(0);
  private static final CallMatcher OPTIONAL_OR_ELSE_GET =
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_OPTIONAL, "orElseGet").parameterCount(1);
  private static final CallMatcher OPTIONAL_MAP =
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_OPTIONAL, "map").parameterCount(1);
  private static final CallMatcher OPTIONAL_OF_NULLABLE =
    CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_OPTIONAL, "ofNullable").parameterCount(1);
  private static final CallMatcher OPTIONAL_OF_OF_NULLABLE =
    CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_OPTIONAL, "ofNullable", "of").parameterCount(1);


  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel8OrHigher(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new OptionalChainVisitor() {
      @Override
      protected void handleSimplification(@NotNull PsiElement element,
                                          @NotNull OptionalChainSimplification simplification) {
        holder.registerProblem(element, simplification.getDescription(), new OptionalChainFix(simplification));
      }
    };
  }

  private static abstract class OptionalChainVisitor extends JavaElementVisitor {
    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression call) {
      if (OPTIONAL_GET.test(call)) {
        handleRewrapping(call, OPTIONAL_OF_OF_NULLABLE);
        return;
      }
      PsiExpression falseArg = null;
      boolean useOrElseGet = false;
      if (OPTIONAL_OR_ELSE.test(call)) {
        falseArg = call.getArgumentList().getExpressions()[0];
      }
      else if (OPTIONAL_OR_ELSE_GET.test(call)) {
        useOrElseGet = true;
        PsiLambdaExpression lambda = getLambda(call.getArgumentList().getExpressions()[0]);
        if (lambda == null || lambda.getParameterList().getParametersCount() != 0) return;
        falseArg = LambdaUtil.extractSingleExpressionFromBody(lambda.getBody());
      }
      if (falseArg == null) return;
      handleMapOrElse(call, useOrElseGet, falseArg);
      if (ExpressionUtils.isNullLiteral(falseArg)) {
        handleRewrapping(call, OPTIONAL_OF_NULLABLE);
      }
      handleOrElseNullConditionalReturn(call, falseArg);
      handleOrElseNullConditionalAction(call, falseArg);
    }

    private void handleRewrapping(PsiMethodCallExpression call, CallMatcher wrapper) {
      PsiElement parent = PsiUtil.skipParenthesizedExprUp(call.getParent());
      if (!(parent instanceof PsiExpressionList)) return;
      PsiMethodCallExpression parentCall = tryCast(parent.getParent(), PsiMethodCallExpression.class);
      if (!wrapper.test(parentCall)) return;
      PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
      if (qualifier == null ||
          !EquivalenceChecker.getCanonicalPsiEquivalence().typesAreEquivalent(qualifier.getType(), parentCall.getType())) {
        return;
      }
      if ("get".equals(call.getMethodExpression().getReferenceName()) &&
          !Boolean.TRUE.equals(CommonDataflow.getExpressionFact(qualifier, DfaFactType.OPTIONAL_PRESENCE))) {
        return;
      }
      SimplifyOptionalChainFix fix = new SimplifyOptionalChainFix(qualifier.getText(), "Unwrap", "Unnecessary Optional rewrapping");
      handleSimplification(Objects.requireNonNull(parentCall.getMethodExpression().getReferenceNameElement()), fix);
    }

    private void handleMapOrElse(PsiMethodCallExpression call, boolean useOrElseGet, PsiExpression falseArg) {
      PsiMethodCallExpression qualifierCall = MethodCallUtils.getQualifierMethodCall(call);
      if (!OPTIONAL_MAP.test(qualifierCall)) return;
      PsiLambdaExpression lambda = getLambda(qualifierCall.getArgumentList().getExpressions()[0]);
      if (lambda == null) return;
      PsiExpression trueArg = LambdaUtil.extractSingleExpressionFromBody(lambda.getBody());
      if (trueArg == null) return;
      PsiParameter[] parameters = lambda.getParameterList().getParameters();
      if (parameters.length != 1) return;
      PsiExpression qualifier = qualifierCall.getMethodExpression().getQualifierExpression();
      if (qualifier == null) return;
      String opt = qualifier.getText();
      PsiParameter parameter = parameters[0];
      String proposed = OptionalUtil.generateOptionalUnwrap(opt, parameter, trueArg, falseArg, call.getType(), useOrElseGet);
      String canonicalOrElse;
      if (useOrElseGet && !ExpressionUtils.isSimpleExpression(falseArg)) {
        canonicalOrElse = ".orElseGet(() -> " + falseArg.getText() + ")";
      }
      else {
        canonicalOrElse = ".orElse(" + falseArg.getText() + ")";
      }
      String canonical = opt + ".map(" + LambdaUtil.createLambda(parameter, trueArg) + ")" + canonicalOrElse;
      if (proposed.length() < canonical.length()) {
        String displayCode;
        if(proposed.equals(opt)) {
          displayCode = "";
        } else if(opt.length() > 10) {
          // should be a parseable expression
          opt = "(($))";
          String template = OptionalUtil.generateOptionalUnwrap(opt, parameter, trueArg, falseArg, call.getType(), useOrElseGet);
          displayCode =
            PsiExpressionTrimRenderer.render(JavaPsiFacade.getElementFactory(parameter.getProject()).createExpressionFromText(template, call));
          displayCode = displayCode.replaceFirst(Pattern.quote(opt), "..");
        } else {
          displayCode =
            PsiExpressionTrimRenderer.render(JavaPsiFacade.getElementFactory(parameter.getProject()).createExpressionFromText(proposed, call));
        }
        String message = displayCode.isEmpty() ? "Remove redundant steps from optional chain" :
                         "Simplify optional chain to '" + displayCode + "'";
        SimplifyOptionalChainFix fix = new SimplifyOptionalChainFix(proposed, message, "Optional chain can be simplified");
        handleSimplification(Objects.requireNonNull(call.getMethodExpression().getReferenceNameElement()), fix);
      }
    }

    private void handleOrElseNullConditionalReturn(PsiMethodCallExpression call, PsiExpression falseArg) {
      OrElseReturnStreamFix.Context context = OrElseReturnStreamFix.Context.extract(call, falseArg);
      if (context == null) return;
      OrElseReturnStreamFix fix = new OrElseReturnStreamFix(context.getDefaultExpression(), context.isSimple());
      handleSimplification(Objects.requireNonNull(call.getMethodExpression().getReferenceNameElement()), fix);
    }

    private void handleOrElseNullConditionalAction(PsiMethodCallExpression call, PsiExpression falseArg) {
      if (OrElseNonNullActionFix.Context.extract(call, falseArg) == null) return;
      OrElseNonNullActionFix fix = new OrElseNonNullActionFix();
      handleSimplification(Objects.requireNonNull(call.getMethodExpression().getReferenceNameElement()), fix);
    }

    protected abstract void handleSimplification(@NotNull PsiElement element, @NotNull OptionalChainSimplification simplification);
  }


  @Nullable
  private static PsiLambdaExpression getLambda(PsiExpression initializer) {
    PsiExpression expression = PsiUtil.skipParenthesizedExprDown(initializer);
    if (expression instanceof PsiLambdaExpression) {
      return (PsiLambdaExpression)expression;
    }
    if (expression instanceof PsiMethodReferenceExpression) {
      return LambdaRefactoringUtil.createLambda((PsiMethodReferenceExpression)expression, true);
    }
    return null;
  }

  @Nullable
  private static PsiExpression extractFalseArg(@NotNull PsiMethodCallExpression call) {
    if (OPTIONAL_OR_ELSE.test(call)) {
      return call.getArgumentList().getExpressions()[0];
    }
    if (OPTIONAL_OR_ELSE_GET.test(call)) {
      PsiLambdaExpression lambda = getLambda(call.getArgumentList().getExpressions()[0]);
      if (lambda == null || lambda.getParameterList().getParametersCount() != 0) return null;
      return LambdaUtil.extractSingleExpressionFromBody(lambda.getBody());
    }
    return null;
  }

  interface OptionalChainSimplification {
    @NotNull
    String getName();

    @NotNull
    String getDescription();

    void applyFix(@NotNull Project project, @NotNull PsiElement element);
  }


  private static class OptionalChainFix implements LocalQuickFix {

    private final @NotNull OptionalChainSimplification mySimplification;

    OptionalChainFix(@NotNull OptionalChainSimplification simplification) {mySimplification = simplification;}

    @Nls
    @NotNull
    @Override
    public String getName() {
      return mySimplification.getName();
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return "Simplify optional call chain";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      mySimplification.applyFix(project, descriptor.getStartElement());
    }
  }


  private static class OrElseReturnStreamFix implements OptionalChainSimplification {
    private final @NotNull String defaultExpression;
    private final boolean myIsSimple;

    private OrElseReturnStreamFix(@NotNull PsiExpression expression, boolean simple) {
      defaultExpression = PsiExpressionTrimRenderer.render(expression);
      myIsSimple = simple;
    }

    @NotNull
    @Override
    public String getName() {
      String method = myIsSimple ? "orElse" : "orElseGet";
      return "Replace null check with " + method + "(" + defaultExpression + ")";
    }

    @NotNull
    @Override
    public String getDescription() {
      return "Remove redundant null check";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull PsiElement element) {
      PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class, false);
      if (call == null) return;
      PsiExpression falseArg = extractFalseArg(call);
      if (!ExpressionUtils.isNullLiteral(falseArg)) return;
      Context context = Context.extract(call, falseArg);
      if (context == null) return;
      PsiExpression receiver = context.getOrElseCall().getMethodExpression().getQualifierExpression();
      if (receiver == null) return;
      String methodWithArg = context.isSimple()
                      ? ".orElse(" + context.getDefaultExpression().getText() + ")"
                      : ".orElseGet(()->" + context.getDefaultExpression().getText() + ")";
      String expressionText;
      expressionText = receiver.getText() + methodWithArg;
      PsiStatement finalStatement = JavaPsiFacade.getElementFactory(project).createStatementFromText("return " + expressionText + ";", receiver);
      PsiStatement current = PsiTreeUtil.getParentOfType(context.getOrElseCall(), PsiStatement.class, false);
      if (current == null) return;
      PsiElement result = new CommentTracker().replaceAndRestoreComments(current, finalStatement);
      new CommentTracker().deleteAndRestoreComments(context.getNextStatement());
      LambdaCanBeMethodReferenceInspection.replaceAllLambdasWithMethodReferences(result);
    }

    /*
    if(optValue != null) {return optValue;} else {return "default";}
    or
    return optValue == null? "default" : optValue;
     */
    @Nullable
    private static PsiExpression extractConditionalDefaultValue(@NotNull PsiStatement statement, @NotNull PsiVariable optValue) {
      if (statement instanceof PsiIfStatement) {
        PsiIfStatement ifStatement = (PsiIfStatement)statement;
        PsiExpression condition = ifStatement.getCondition();
        if (condition == null) return null;
        PsiExpression thenExpr = getReturnExpression(ifStatement.getThenBranch());
        PsiExpression elseExpr = getReturnExpression(ifStatement.getElseBranch());
        if (thenExpr == null || elseExpr == null) return null;
        return extractConditionalDefaultValue(thenExpr, elseExpr, condition, optValue);
      }
      else if (statement instanceof PsiReturnStatement) {
        PsiExpression returnValue = ((PsiReturnStatement)statement).getReturnValue();
        PsiConditionalExpression ternary = tryCast(PsiUtil.skipParenthesizedExprDown(returnValue), PsiConditionalExpression.class);
        if (ternary == null) return null;
        PsiExpression thenExpression = ternary.getThenExpression();
        PsiExpression elseExpression = ternary.getElseExpression();
        if(thenExpression == null || elseExpression == null) return null;
        return extractConditionalDefaultValue(thenExpression, elseExpression, ternary.getCondition(), optValue);
      }
      return null;
    }

    @Contract("null -> null")
    @Nullable
    private static PsiExpression getReturnExpression(@Nullable PsiStatement block) {
      if (block == null) return null;
      PsiStatement statement = ControlFlowUtils.stripBraces(block);
      PsiReturnStatement returnStatement = tryCast(statement, PsiReturnStatement.class);
      if (returnStatement == null) return null;
      return returnStatement.getReturnValue();
    }

    @Nullable
    private static PsiExpression extractConditionalDefaultValue(@NotNull PsiExpression thenExpr,
                                                                @NotNull PsiExpression elseExpr,
                                                                @NotNull PsiExpression condition,
                                                                @NotNull PsiVariable optValue) {
      PsiVariable nullChecked = ExpressionUtils.getVariableFromNullComparison(condition, true);
      boolean inverted = false;
      if (nullChecked == null) {
        nullChecked = ExpressionUtils.getVariableFromNullComparison(condition, false);
        if (nullChecked == null) return null;
        inverted = true;
      }
      if (!nullChecked.equals(optValue) || !ExpressionUtils.isReferenceTo(inverted ? thenExpr : elseExpr, optValue)) return null;
      PsiExpression defaultExpression = inverted ? elseExpr : thenExpr;
      if (VariableAccessUtils.variableIsUsed(optValue, defaultExpression)) return null;
      return defaultExpression;
    }

    private static class Context {
      @NotNull private final PsiMethodCallExpression myOrElseCall;
      @NotNull private final PsiExpression myDefaultExpression;
      @NotNull private final PsiStatement myNextStatement;
      private final boolean mySimple;

      private Context(@NotNull PsiMethodCallExpression call,
                      @NotNull PsiExpression defaultExpression,
                      @NotNull PsiStatement nextStatement, boolean simple) {
        myOrElseCall = call;
        myDefaultExpression = defaultExpression;
        myNextStatement = nextStatement;
        mySimple = simple;
      }

      @NotNull
      public PsiStatement getNextStatement() {
        return myNextStatement;
      }


      @NotNull
      public PsiMethodCallExpression getOrElseCall() {
        return myOrElseCall;
      }

      @NotNull
      public PsiExpression getDefaultExpression() {
        return myDefaultExpression;
      }

      public boolean isSimple() {
        return mySimple;
      }

      @Nullable
      static Context extract(@NotNull PsiMethodCallExpression call, @NotNull PsiExpression falseArg) {
        if (!ExpressionUtils.isNullLiteral(falseArg)) return null;
        PsiLocalVariable returnVar = PsiTreeUtil.getParentOfType(call, PsiLocalVariable.class, true);
        if (returnVar == null) return null;
        PsiStatement nextStatement =
          tryCast(PsiTreeUtil.skipWhitespacesForward(returnVar.getParent()), PsiStatement.class);
        if (nextStatement == null) return null;
        PsiExpression defaultValue = extractConditionalDefaultValue(nextStatement, returnVar);
        boolean isSimple = ExpressionUtils.isSimpleExpression(defaultValue);
        if (defaultValue == null || (!isSimple && !LambdaGenerationUtil.canBeUncheckedLambda(defaultValue))) return null;
        PsiType type = defaultValue.getType();
        PsiType methodCallReturnValue = call.getMethodExpression().getType();
        if(type == null || methodCallReturnValue == null || !methodCallReturnValue.isAssignableFrom(type)) return null;
        return new Context(call, defaultValue, nextStatement, isSimple);
      }
    }
  }


  private static class OrElseNonNullActionFix implements OptionalChainSimplification {
    @NotNull
    @Override
    public String getName() {
      return "Replace null check with ifPresent()";
    }

    @NotNull
    @Override
    public String getDescription() {
      return "Remove redundant null check";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull PsiElement element) {
      PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class, false);
      if (call == null) return;
      PsiExpression falseArg = extractFalseArg(call);
      if (!ExpressionUtils.isNullLiteral(falseArg)) return;
      Context context = Context.extract(call, falseArg);
      if(context == null) return;
      PsiExpression receiver = context.getOrElseCall().getMethodExpression().getQualifierExpression();
      if(receiver == null) return;
      String statementText = receiver.getText() + ".ifPresent(" + LambdaUtil.createLambda(context.getVariable(), context.getAction()) + ");";
      PsiStatement finalStatement = JavaPsiFacade.getElementFactory(project).createStatementFromText(statementText, context.getStatement());
      PsiElement result = context.getStatement().replace(finalStatement);
      context.getConditionStatement().delete();
      LambdaCanBeMethodReferenceInspection.replaceAllLambdasWithMethodReferences(result);
    }

    private static class Context {
      private final @NotNull PsiExpression myAction;
      private final @NotNull PsiStatement myConditionStatement;
      private final @NotNull PsiStatement myStatement;
      private final @NotNull PsiVariable myVariable;
      private final @NotNull PsiMethodCallExpression myOrElseCall;

      @NotNull
      public PsiExpression getAction() {
        return myAction;
      }

      @NotNull
      public PsiStatement getConditionStatement() {
        return myConditionStatement;
      }

      @NotNull
      public PsiStatement getStatement() {
        return myStatement;
      }

      @NotNull
      public PsiMethodCallExpression getOrElseCall() {
        return myOrElseCall;
      }

      private Context(@NotNull PsiExpression action,
                      @NotNull PsiStatement conditionStatement,
                      @NotNull PsiStatement statement,
                      @NotNull PsiVariable variable, @NotNull PsiMethodCallExpression call) {
        myAction = action;
        myConditionStatement = conditionStatement;
        myStatement = statement;
        myVariable = variable;
        myOrElseCall = call;
      }

      @NotNull
      public PsiVariable getVariable() {
        return myVariable;
      }

      @Nullable
      static Context extract(@NotNull PsiMethodCallExpression orElseCall, @NotNull PsiExpression orElseArgument) {
        if (!ExpressionUtils.isNullLiteral(orElseArgument)) return null;
        PsiLocalVariable returnVar = tryCast(orElseCall.getParent(), PsiLocalVariable.class);
        if (returnVar == null) return null;
        PsiStatement statement = PsiTreeUtil.getParentOfType(returnVar, PsiStatement.class, true);
        if(statement == null) return null;
        PsiStatement nextStatement =
          tryCast(PsiTreeUtil.skipWhitespacesForward(returnVar.getParent()), PsiStatement.class);
        if (nextStatement == null) return null;
        PsiExpression lambdaExpr = extractMappingExpression(nextStatement, returnVar);
        if (lambdaExpr == null || !LambdaGenerationUtil.canBeUncheckedLambda(lambdaExpr)) return null;
        if(!ReferencesSearch.search(returnVar).forEach(reference ->
                                                     PsiTreeUtil.isAncestor(statement, reference.getElement(), false) ||
                                                     PsiTreeUtil.isAncestor(nextStatement, reference.getElement(), false))) return null;
        return new Context(lambdaExpr, nextStatement, statement, returnVar, orElseCall);
      }


    }


    /*
      if(optValue != null) {
        System.out.println(optValue);
      }
     */
    @Nullable
    private static PsiExpression extractMappingExpression(@NotNull PsiStatement statement, @NotNull PsiVariable optValue) {
      PsiIfStatement ifStatement = tryCast(statement, PsiIfStatement.class);
      if (ifStatement == null) return null;
      if (ifStatement.getElseBranch() != null) return null;
      PsiExpression condition = ifStatement.getCondition();
      if (condition == null) return null;
      if (ExpressionUtils.getVariableFromNullComparison(condition, false) != optValue) return null;

      PsiStatement thenStatement = ControlFlowUtils.stripBraces(ifStatement.getThenBranch());
      PsiExpressionStatement expressionStatement = tryCast(thenStatement, PsiExpressionStatement.class);
      if (expressionStatement == null) return null;
      return expressionStatement.getExpression();
    }
  }


  private static class SimplifyOptionalChainFix implements OptionalChainSimplification {
    private final String myReplacement;
    private final String myMessage;
    private final String myDescription;

    public SimplifyOptionalChainFix(String replacement, String message, String description) {
      myReplacement = replacement;
      myMessage = message;
      myDescription = description;
    }

    @NotNull
    @Override
    public String getName() {
      return myMessage;
    }

    @NotNull
    @Override
    public String getDescription() {
      return myDescription;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull PsiElement element) {
      PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
      if (call == null) return;
      PsiExpression replacementExpression = JavaPsiFacade.getElementFactory(project).createExpressionFromText(myReplacement, call);
      PsiElement result = call.replace(replacementExpression);
      LambdaCanBeMethodReferenceInspection.replaceAllLambdasWithMethodReferences(result);
      PsiDiamondTypeUtil.removeRedundantTypeArguments(result);
    }
  }
}
