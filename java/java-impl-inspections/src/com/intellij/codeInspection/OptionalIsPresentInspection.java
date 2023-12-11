// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.dataFlow.NullabilityUtil;
import com.intellij.codeInspection.util.LambdaGenerationUtil;
import com.intellij.codeInspection.util.OptionalRefactoringUtil;
import com.intellij.codeInspection.util.OptionalUtil;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

import static com.intellij.codeInsight.PsiEquivalenceUtil.areElementsEquivalent;

public final class OptionalIsPresentInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance(OptionalIsPresentInspection.class);

  private static final CallMatcher OPTIONAL_IS_PRESENT =
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_OPTIONAL, "isPresent").parameterCount(0);

  private static final CallMatcher OPTIONAL_IS_EMPTY =
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_OPTIONAL, "isEmpty").parameterCount(0);

  private static final OptionalIsPresentCase[] CASES = OptionalIsPresentCase.values();

  private enum ProblemType {
    WARNING, INFO, NONE;

    void registerProblem(@NotNull ProblemsHolder holder, @NotNull PsiExpression condition, OptionalIsPresentCase scenario) {
      if(this != NONE) {
        if (this == INFO && !holder.isOnTheFly()) {
          return; //don't register fixes in batch mode
        }
        holder.registerProblem(condition, JavaBundle.message(
          "inspection.message.can.be.replaced.with.single.expression.in.functional.style"),
                               this == INFO ? ProblemHighlightType.INFORMATION : ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                               new OptionalIsPresentFix(scenario));
      }
    }
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel8OrHigher(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitConditionalExpression(@NotNull PsiConditionalExpression expression) {
        super.visitConditionalExpression(expression);
        PsiExpression condition = PsiUtil.skipParenthesizedExprDown(expression.getCondition());
        if (condition == null) return;
        PsiReferenceExpression optionalRef = extractOptionalFromPresenceCheck(condition);
        if (optionalRef == null) return;
        boolean invert = isEmptyCheck(condition);
        PsiExpression thenExpression = invert ? expression.getElseExpression() : expression.getThenExpression();
        PsiExpression elseExpression = invert ? expression.getThenExpression() : expression.getElseExpression();
        check(condition, optionalRef, thenExpression, elseExpression);
      }

      @Override
      public void visitIfStatement(@NotNull PsiIfStatement statement) {
        super.visitIfStatement(statement);
        PsiExpression condition = PsiUtil.skipParenthesizedExprDown(statement.getCondition());
        if (condition == null) return;
        PsiReferenceExpression optionalRef = extractOptionalFromPresenceCheck(condition);
        if (optionalRef == null) return;
        boolean invert = isEmptyCheck(condition);
        PsiStatement thenStatement = extractThenStatement(statement, invert);
        PsiStatement elseStatement = extractElseStatement(statement, invert);
        check(condition, optionalRef, thenStatement, elseStatement);
      }

      void check(@NotNull PsiExpression condition, PsiReferenceExpression optionalRef, PsiElement thenElement, PsiElement elseElement) {
        for (OptionalIsPresentCase scenario : CASES) {
          scenario.getProblemType(optionalRef, thenElement, elseElement).registerProblem(holder, condition, scenario);
        }
      }
    };
  }

  private static boolean isRaw(@NotNull PsiVariable variable) {
    PsiType type = variable.getType();
    return type instanceof PsiClassType && ((PsiClassType)type).isRaw();
  }

  @Nullable
  private static PsiStatement extractThenStatement(@NotNull PsiIfStatement ifStatement, boolean invert) {
    if (invert) return extractElseStatement(ifStatement, false);
    return ControlFlowUtils.stripBraces(ifStatement.getThenBranch());
  }

  @Nullable
  private static PsiStatement extractElseStatement(@NotNull PsiIfStatement ifStatement, boolean invert) {
    if (invert) return extractThenStatement(ifStatement, false);
    PsiStatement statement = ControlFlowUtils.stripBraces(ifStatement.getElseBranch());
    if (statement == null) {
      PsiStatement thenStatement = extractThenStatement(ifStatement, false);
      if (thenStatement instanceof PsiReturnStatement) {
        PsiElement nextElement = PsiTreeUtil.skipWhitespacesAndCommentsForward(ifStatement);
        if (nextElement instanceof PsiStatement) {
          statement = ControlFlowUtils.stripBraces((PsiStatement)nextElement);
        }
      }
    }
    return statement;
  }

  @Nullable
  @Contract("null -> null")
  private static PsiReferenceExpression extractOptionalFromPresenceCheck(PsiExpression condition) {
    while (condition != null && BoolUtils.isNegation(condition)) {
      condition = BoolUtils.getNegated(condition);
    }
    PsiMethodCallExpression call = ObjectUtils.tryCast(condition, PsiMethodCallExpression.class);
    if (!OPTIONAL_IS_PRESENT.matches(call) && !OPTIONAL_IS_EMPTY.matches(call)) return null;
    PsiReferenceExpression qualifier =
      ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(call.getMethodExpression().getQualifierExpression()), PsiReferenceExpression.class);
    if (qualifier == null) return null;
    PsiElement element = qualifier.resolve();
    if (!(element instanceof PsiVariable) || isRaw((PsiVariable)element)) return null;
    return qualifier;
  }

  /**
   * @param condition condition which is known to represent either isPresent() or isEmpty() check
   * @return true if condition represents isEmpty() check; false if it's isPresent() check
   */
  private static boolean isEmptyCheck(PsiExpression condition) {
    boolean invert = false;
    while (condition != null && BoolUtils.isNegation(condition)) {
      condition = BoolUtils.getNegated(condition);
      invert = !invert;
    }
    return OPTIONAL_IS_EMPTY.matches(condition) != invert;
  }

  @Contract("null, _ -> false")
  static boolean isOptionalGetCall(PsiElement element, @NotNull PsiReferenceExpression optionalRef) {
    PsiMethodCallExpression call = ObjectUtils.tryCast(element, PsiMethodCallExpression.class);
    if (!OptionalUtil.JDK_OPTIONAL_GET.matches(call)) return false;
    PsiExpression qualifier = ExpressionUtils.getEffectiveQualifier(call.getMethodExpression());
    return qualifier != null && areElementsEquivalent(qualifier, optionalRef);
  }

  @NotNull
  static ProblemType getTypeByLambdaCandidate(@NotNull PsiReferenceExpression optionalRef,
                                              @Nullable PsiElement lambdaCandidate,
                                              @Nullable PsiExpression falseExpression) {
    if (lambdaCandidate == null) return ProblemType.NONE;
    if (lambdaCandidate instanceof PsiReferenceExpression &&
        areElementsEquivalent(lambdaCandidate, optionalRef) && OptionalUtil.isOptionalEmptyCall(falseExpression)) {
      return ProblemType.WARNING;
    }
    if (!LambdaGenerationUtil.canBeUncheckedLambda(lambdaCandidate, optionalRef::isReferenceTo)) return ProblemType.NONE;
    Ref<Boolean> hasOptionalReference = new Ref<>(Boolean.FALSE);
    boolean hasNoBadRefs = PsiTreeUtil.processElements(lambdaCandidate, e -> {
      if (!(e instanceof PsiReferenceExpression)) return true;
      if (!areElementsEquivalent(e, optionalRef)) return true;
      // Check that Optional variable is referenced only in context of get() call
      hasOptionalReference.set(Boolean.TRUE);
      return isOptionalGetCall(e.getParent().getParent(), optionalRef);
    });
    if (!hasNoBadRefs) return ProblemType.NONE;
    if (!hasOptionalReference.get() || !(lambdaCandidate instanceof PsiExpression expression)) return ProblemType.INFO;
    if (falseExpression != null) {
      // falseExpression == null is "consumer" case (to be replaced with ifPresent())
      if (!ExpressionUtils.isNullLiteral(falseExpression) &&
          NullabilityUtil.getExpressionNullability(expression, true) != Nullability.NOT_NULL) {
        // if falseExpression is null literal, then semantics is preserved
        return ProblemType.INFO;
      }
      PsiType falseType = falseExpression.getType();
      PsiType trueType = expression.getType();
      if (falseType == null || trueType == null) return ProblemType.NONE;
      if (falseType instanceof PsiPrimitiveType && trueType instanceof PsiPrimitiveType) {
        if (falseType.equals(trueType) || JavaPsiMathUtil.getNumberFromLiteral(falseExpression) != null) {
          // like x ? double_expression : integer_expression; support only if integer_expression is simple literal,
          // so could be converted explicitly to double
          return ProblemType.WARNING;
        }
        return ProblemType.NONE;
      }
      if (!trueType.isAssignableFrom(falseType)) {
        return ProblemType.NONE;
      }
    }
    return ProblemType.WARNING;
  }

  @NotNull
  static String generateOptionalLambda(@NotNull PsiElementFactory factory,
                                       @NotNull CommentTracker ct,
                                       PsiReferenceExpression optionalRef,
                                       PsiElement trueValue) {
    PsiType type = optionalRef.getType();
    String paramName = new VariableNameGenerator(trueValue, VariableKind.PARAMETER)
      .byType(OptionalUtil.getOptionalElementType(type)).byName("value").generate(true);
    if(trueValue instanceof PsiExpressionStatement) {
      trueValue = ((PsiExpressionStatement)trueValue).getExpression();
    }
    ct.markUnchanged(trueValue);
    PsiElement copy = trueValue.copy();
    for (PsiElement getCall : PsiTreeUtil.collectElements(copy, e -> isOptionalGetCall(e, optionalRef))) {
      PsiElement result = getCall.replace(factory.createIdentifier(paramName));
      if (copy == getCall) copy = result;
    }
    if(copy instanceof PsiStatement && !(copy instanceof PsiBlockStatement)) {
      return paramName + "->{" + copy.getText()+"}";
    }
    return paramName + "->" + copy.getText();
  }

  static String generateOptionalUnwrap(@NotNull PsiElementFactory factory,
                                       @NotNull CommentTracker ct,
                                       @NotNull PsiReferenceExpression optionalRef,
                                       @NotNull PsiExpression trueValue,
                                       @NotNull PsiExpression falseValue,
                                       PsiType targetType) {
    if (areElementsEquivalent(trueValue, optionalRef) && OptionalUtil.isOptionalEmptyCall(falseValue)) {
      trueValue =
        factory.createExpressionFromText(CommonClassNames.JAVA_UTIL_OPTIONAL + ".of(" + optionalRef.getText() + ".get())", trueValue);
    }
    if (areElementsEquivalent(falseValue, optionalRef)) {
      falseValue = factory.createExpressionFromText(CommonClassNames.JAVA_UTIL_OPTIONAL + ".empty()", falseValue);
    }
    String lambdaText = generateOptionalLambda(factory, ct, optionalRef, trueValue);
    PsiLambdaExpression lambda = (PsiLambdaExpression)factory.createExpressionFromText(lambdaText, trueValue);
    PsiExpression body = Objects.requireNonNull((PsiExpression)lambda.getBody());
    return OptionalRefactoringUtil.generateOptionalUnwrap(optionalRef.getText(), lambda.getParameterList().getParameters()[0],
                                                          body, ct.markUnchanged(falseValue), targetType, true);
  }

  static boolean isSimpleOrUnchecked(PsiExpression expression) {
    return ExpressionUtils.isSafelyRecomputableExpression(expression) || LambdaGenerationUtil.canBeUncheckedLambda(expression);
  }

  static class OptionalIsPresentFix extends PsiUpdateModCommandQuickFix {
    private final OptionalIsPresentCase myScenario;

    OptionalIsPresentFix(OptionalIsPresentCase scenario) {
      myScenario = scenario;
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return JavaBundle.message("intention.family.replace.optional.ispresent.condition.with.functional.style.expression");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (!(element instanceof PsiExpression condition)) return;
      PsiReferenceExpression optionalRef = extractOptionalFromPresenceCheck(condition);
      if (optionalRef == null) return;
      PsiElement cond = PsiTreeUtil.getParentOfType(element, PsiIfStatement.class, PsiConditionalExpression.class);
      PsiElement thenElement;
      PsiElement elseElement;
      boolean invert = isEmptyCheck(condition);
      if (cond instanceof PsiIfStatement ifStatement) {
        thenElement = extractThenStatement(ifStatement, invert);
        elseElement = extractElseStatement(ifStatement, invert);
      }
      else if (cond instanceof PsiConditionalExpression ternary) {
        thenElement = invert ? ternary.getElseExpression() : ternary.getThenExpression();
        elseElement = invert ? ternary.getThenExpression() : ternary.getElseExpression();
      }
      else {
        return;
      }
      if (myScenario.getProblemType(optionalRef, thenElement, elseElement) == ProblemType.NONE) {
        // Probably the code was modified
        return;
      }
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      CommentTracker ct = new CommentTracker();
      String replacementText = myScenario.generateReplacement(factory, ct, optionalRef, thenElement, elseElement);
      if (thenElement != null && !PsiTreeUtil.isAncestor(cond, thenElement, true)) ct.delete(thenElement);
      if (elseElement != null && !PsiTreeUtil.isAncestor(cond, elseElement, true)) ct.delete(elseElement);
      PsiElement result = ct.replaceAndRestoreComments(cond, replacementText);
      LambdaCanBeMethodReferenceInspection.replaceAllLambdasWithMethodReferences(result);
      RemoveRedundantTypeArgumentsUtil.removeRedundantTypeArguments(result);
      CodeStyleManager.getInstance(project).reformat(result);
    }
  }

  enum OptionalIsPresentCase {
    RETURN_CASE {
      @NotNull
      @Override
      public ProblemType getProblemType(@NotNull PsiReferenceExpression optionalRef,
                                        @Nullable PsiElement trueElement,
                                        @Nullable PsiElement falseElement) {
        if (!(trueElement instanceof PsiReturnStatement trueReturn) || !(falseElement instanceof PsiReturnStatement falseReturn)) return ProblemType.NONE;
        PsiExpression falseValue = falseReturn.getReturnValue();
        PsiExpression trueValue = trueReturn.getReturnValue();
        if (!isSimpleOrUnchecked(falseValue)) return ProblemType.NONE;
        return getTypeByLambdaCandidate(optionalRef, trueValue, falseValue);
      }

      @NotNull
      @Override
      public String generateReplacement(@NotNull PsiElementFactory factory,
                                        @NotNull CommentTracker ct, @NotNull PsiReferenceExpression optionalVariable,
                                        PsiElement trueElement,
                                        PsiElement falseElement) {
        PsiExpression trueValue = ((PsiReturnStatement)trueElement).getReturnValue();
        PsiExpression falseValue = ((PsiReturnStatement)falseElement).getReturnValue();
        LOG.assertTrue(trueValue != null);
        LOG.assertTrue(falseValue != null);
        return "return " +
               generateOptionalUnwrap(factory, ct, optionalVariable, trueValue, falseValue, PsiTypesUtil.getMethodReturnType(trueElement)) +
               ";";
      }
    },

    ASSIGNMENT_CASE {
      @NotNull
      @Override
      public ProblemType getProblemType(@NotNull PsiReferenceExpression optionalVariable,
                                        @Nullable PsiElement trueElement,
                                        @Nullable PsiElement falseElement) {
        PsiAssignmentExpression trueAssignment = ExpressionUtils.getAssignment(trueElement);
        PsiAssignmentExpression falseAssignment = ExpressionUtils.getAssignment(falseElement);
        if (trueAssignment == null || falseAssignment == null) return ProblemType.NONE;
        PsiExpression falseVal = falseAssignment.getRExpression();
        PsiExpression trueVal = trueAssignment.getRExpression();
        if (areElementsEquivalent(trueAssignment.getLExpression(), falseAssignment.getLExpression()) &&
            isSimpleOrUnchecked(falseVal)) {
          return getTypeByLambdaCandidate(optionalVariable, trueVal, falseVal);
        }
        return ProblemType.NONE;
      }

      @NotNull
      @Override
      public String generateReplacement(@NotNull PsiElementFactory factory,
                                        @NotNull CommentTracker ct,
                                        @NotNull PsiReferenceExpression optionalRef,
                                        PsiElement trueElement,
                                        PsiElement falseElement) {
        PsiAssignmentExpression trueAssignment = ExpressionUtils.getAssignment(trueElement);
        PsiAssignmentExpression falseAssignment = ExpressionUtils.getAssignment(falseElement);
        LOG.assertTrue(trueAssignment != null);
        LOG.assertTrue(falseAssignment != null);
        PsiExpression lValue = trueAssignment.getLExpression();
        PsiExpression trueValue = trueAssignment.getRExpression();
        PsiExpression falseValue = falseAssignment.getRExpression();
        LOG.assertTrue(trueValue != null);
        LOG.assertTrue(falseValue != null);
        return lValue.getText() + " = " + generateOptionalUnwrap(factory, ct, optionalRef, trueValue, falseValue, lValue.getType()) + ";";
      }
    },

    TERNARY_CASE {
      @NotNull
      @Override
      public ProblemType getProblemType(@NotNull PsiReferenceExpression optionalVariable,
                                        @Nullable PsiElement trueElement,
                                        @Nullable PsiElement falseElement) {
        if(!(trueElement instanceof PsiExpression trueExpression) || !(falseElement instanceof PsiExpression falseExpression)) return ProblemType.NONE;
        PsiType trueType = trueExpression.getType();
        PsiType falseType = falseExpression.getType();
        if (trueType == null || falseType == null || !trueType.isAssignableFrom(falseType) || !isSimpleOrUnchecked(falseExpression)) {
          return ProblemType.NONE;
        }
        return getTypeByLambdaCandidate(optionalVariable, trueExpression, falseExpression);
      }

      @NotNull
      @Override
      public String generateReplacement(@NotNull PsiElementFactory factory,
                                        @NotNull CommentTracker ct,
                                        @NotNull PsiReferenceExpression optionalVariable,
                                        PsiElement trueElement,
                                        PsiElement falseElement) {
        PsiExpression ternary = PsiTreeUtil.getParentOfType(trueElement, PsiConditionalExpression.class);
        LOG.assertTrue(ternary != null);
        PsiExpression trueExpression = (PsiExpression)trueElement;
        PsiExpression falseExpression = (PsiExpression)falseElement;
        return generateOptionalUnwrap(factory, ct, optionalVariable, trueExpression, falseExpression, ternary.getType());
      }
    },

    CONSUMER_CASE {
      @NotNull
      @Override
      public ProblemType getProblemType(@NotNull PsiReferenceExpression optionalRef,
                                        @Nullable PsiElement trueElement,
                                        @Nullable PsiElement falseElement) {
        if (falseElement != null && !(falseElement instanceof PsiEmptyStatement)) return ProblemType.NONE;
        if (!(trueElement instanceof PsiStatement)) return ProblemType.NONE;
        if (trueElement instanceof PsiExpressionStatement) {
          PsiExpression expression = ((PsiExpressionStatement)trueElement).getExpression();
          if (isOptionalGetCall(expression, optionalRef)) return ProblemType.NONE;
          trueElement = expression;
        }
        return getTypeByLambdaCandidate(optionalRef, trueElement, null);
      }

      @NotNull
      @Override
      public String generateReplacement(@NotNull PsiElementFactory factory,
                                        @NotNull CommentTracker ct,
                                        @NotNull PsiReferenceExpression optionalRef,
                                        PsiElement trueElement,
                                        PsiElement falseElement) {
        return optionalRef.getText() + ".ifPresent(" + generateOptionalLambda(factory, ct, optionalRef, trueElement) + ");";
      }
    };

    @NotNull
    abstract ProblemType getProblemType(@NotNull PsiReferenceExpression optionalVariable,
                               @Nullable PsiElement trueElement,
                               @Nullable PsiElement falseElement);

    @NotNull
    abstract String generateReplacement(@NotNull PsiElementFactory factory,
                               @NotNull CommentTracker ct,
                               @NotNull PsiReferenceExpression optionalVariable,
                               PsiElement trueElement,
                               PsiElement falseElement);
  }
}
