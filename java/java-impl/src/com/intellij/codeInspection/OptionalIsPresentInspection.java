/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInspection.dataFlow.Nullness;
import com.intellij.codeInspection.dataFlow.NullnessUtil;
import com.intellij.codeInspection.util.LambdaGenerationUtil;
import com.intellij.codeInspection.util.OptionalUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.PsiDiamondTypeUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Tagir Valeev
 */
public class OptionalIsPresentInspection extends BaseJavaBatchLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance(OptionalIsPresentInspection.class);

  private static final OptionalIsPresentCase[] CASES = {
    new ReturnCase(),
    new AssignmentCase(),
    new ConsumerCase(),
    new TernaryCase()
  };

  private enum ProblemType {
    WARNING, INFO, NONE;

    void registerProblem(ProblemsHolder holder, PsiExpression condition, OptionalIsPresentCase scenario) {
      if(this != NONE) {
        holder.registerProblem(holder.getManager().createProblemDescriptor(condition,
                                                                           "Can be replaced with single expression in functional style",
                                                                           this != INFO,
                                                                           this == INFO ? ProblemHighlightType.INFORMATION : ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                                                           true,
                                                                           new OptionalIsPresentFix(scenario)));
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
      public void visitConditionalExpression(PsiConditionalExpression expression) {
        super.visitConditionalExpression(expression);
        PsiExpression condition = PsiUtil.skipParenthesizedExprDown(expression.getCondition());
        if (condition == null) return;
        boolean invert = false;
        PsiExpression strippedCondition = condition;
        if (BoolUtils.isNegation(condition)) {
          strippedCondition = BoolUtils.getNegated(condition);
          invert = true;
        }
        PsiVariable optionalVariable = extractOptionalFromIfPresentCheck(strippedCondition);
        if (optionalVariable == null) return;
        PsiExpression thenExpression = invert ? expression.getElseExpression() : expression.getThenExpression();
        PsiExpression elseExpression = invert ? expression.getThenExpression() : expression.getElseExpression();
        check(condition, optionalVariable, thenExpression, elseExpression);
      }

      @Override
      public void visitIfStatement(PsiIfStatement statement) {
        super.visitIfStatement(statement);
        PsiExpression condition = PsiUtil.skipParenthesizedExprDown(statement.getCondition());
        if (condition == null) return;
        boolean invert = false;
        PsiExpression strippedCondition = condition;
        if (BoolUtils.isNegation(condition)) {
          strippedCondition = BoolUtils.getNegated(condition);
          invert = true;
        }
        PsiVariable optionalVariable = extractOptionalFromIfPresentCheck(strippedCondition);
        if (optionalVariable == null) return;
        PsiStatement thenStatement = extractThenStatement(statement, invert);
        PsiStatement elseStatement = extractElseStatement(statement, invert);
        check(condition, optionalVariable, thenStatement, elseStatement);
      }

      void check(PsiExpression condition, PsiVariable optionalVariable, PsiElement thenElement, PsiElement elseElement) {
        for (OptionalIsPresentCase scenario : CASES) {
          scenario.getProblemType(optionalVariable, thenElement, elseElement).registerProblem(holder, condition, scenario);
        }
      }
    };
  }

  private static boolean isRaw(PsiVariable variable) {
    PsiType type = variable.getType();
    return type instanceof PsiClassType && ((PsiClassType)type).isRaw();
  }

  @Nullable
  private static PsiStatement extractThenStatement(PsiIfStatement ifStatement, boolean invert) {
    if (invert) return extractElseStatement(ifStatement, false);
    return ControlFlowUtils.stripBraces(ifStatement.getThenBranch());
  }

  private static PsiStatement extractElseStatement(PsiIfStatement ifStatement, boolean invert) {
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

  @Contract("null -> null")
  static PsiVariable extractOptionalFromIfPresentCheck(PsiExpression expression) {
    if (!(expression instanceof PsiMethodCallExpression)) return null;
    PsiMethodCallExpression call = (PsiMethodCallExpression)expression;
    if (call.getArgumentList().getExpressions().length != 0) return null;
    if (!"isPresent".equals(call.getMethodExpression().getReferenceName())) return null;
    PsiMethod method = call.resolveMethod();
    if (method == null) return null;
    PsiClass containingClass = method.getContainingClass();
    if (containingClass == null || !CommonClassNames.JAVA_UTIL_OPTIONAL.equals(containingClass.getQualifiedName())) return null;
    PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
    if (!(qualifier instanceof PsiReferenceExpression)) return null;
    PsiElement element = ((PsiReferenceExpression)qualifier).resolve();
    if (!(element instanceof PsiVariable) || isRaw((PsiVariable)element)) return null;
    return (PsiVariable)element;
  }

  @Contract("null, _ -> false")
  static boolean isOptionalGetCall(PsiElement element, PsiVariable variable) {
    if (!(element instanceof PsiMethodCallExpression)) return false;
    PsiMethodCallExpression call = (PsiMethodCallExpression)element;
    if (call.getArgumentList().getExpressions().length != 0) return false;
    PsiReferenceExpression methodExpression = call.getMethodExpression();
    return "get".equals(methodExpression.getReferenceName()) &&
           ExpressionUtils.isReferenceTo(methodExpression.getQualifierExpression(), variable);
  }

  @NotNull
  static ProblemType getTypeByLambdaCandidate(PsiVariable optionalVariable, PsiElement lambdaCandidate, PsiExpression falseExpression) {
    if (lambdaCandidate == null) return ProblemType.NONE;
    if (lambdaCandidate instanceof PsiReferenceExpression &&
        ((PsiReferenceExpression)lambdaCandidate).isReferenceTo(optionalVariable) && OptionalUtil.isOptionalEmptyCall(falseExpression)) {
      return ProblemType.WARNING;
    }
    if (!LambdaGenerationUtil.canBeUncheckedLambda(lambdaCandidate, optionalVariable::equals)) return ProblemType.NONE;
    Ref<Boolean> hasOptionalReference = new Ref<>(Boolean.FALSE);
    boolean hasNoBadRefs = PsiTreeUtil.processElements(lambdaCandidate, e -> {
      if (!(e instanceof PsiReferenceExpression)) return true;
      PsiElement element = ((PsiReferenceExpression)e).resolve();
      if (element != optionalVariable) return true;
      // Check that Optional variable is referenced only in context of get() call
      hasOptionalReference.set(Boolean.TRUE);
      return isOptionalGetCall(e.getParent().getParent(), optionalVariable);
    });
    if(!hasNoBadRefs) return ProblemType.NONE;
    if (!hasOptionalReference.get() || !(lambdaCandidate instanceof PsiExpression)) return ProblemType.INFO;
    PsiExpression expression = (PsiExpression)lambdaCandidate;
    if (falseExpression != null && NullnessUtil.getExpressionNullness(expression) != Nullness.NOT_NULL) {
      // falseExpression == null is "consumer" case (to be replaced with ifPresent()),
      // in this case we don't care about expression nullness
      return ProblemType.INFO;
    }
    return ProblemType.WARNING;
  }

  @NotNull
  static String generateOptionalLambda(PsiElementFactory factory, CommentTracker ct, PsiVariable optionalVariable, PsiElement trueValue) {
    PsiType type = optionalVariable.getType();
    JavaCodeStyleManager javaCodeStyleManager = JavaCodeStyleManager.getInstance(trueValue.getProject());
    SuggestedNameInfo info = javaCodeStyleManager.suggestVariableName(VariableKind.PARAMETER, null, null, type);
    if (info.names.length == 0) {
      info = javaCodeStyleManager.suggestVariableName(VariableKind.PARAMETER, "value", null, type);
    }
    String paramName = javaCodeStyleManager.suggestUniqueVariableName(info, trueValue, true).names[0];
    if(trueValue instanceof PsiExpressionStatement) {
      trueValue = ((PsiExpressionStatement)trueValue).getExpression();
    }
    ct.markUnchanged(trueValue);
    PsiElement copy = trueValue.copy();
    for (PsiElement getCall : PsiTreeUtil.collectElements(copy, e -> isOptionalGetCall(e, optionalVariable))) {
      PsiElement result = getCall.replace(factory.createIdentifier(paramName));
      if (copy == getCall) copy = result;
    }
    if(copy instanceof PsiStatement && !(copy instanceof PsiBlockStatement)) {
      return paramName + "->{" + copy.getText()+"}";
    }
    return paramName + "->" + copy.getText();
  }

  static String generateOptionalUnwrap(PsiElementFactory factory,
                                       CommentTracker ct, PsiVariable optionalVariable,
                                       PsiExpression trueValue,
                                       PsiExpression falseValue,
                                       PsiType targetType) {
    if (ExpressionUtils.isReferenceTo(trueValue, optionalVariable) && OptionalUtil.isOptionalEmptyCall(falseValue)) {
      trueValue =
        factory.createExpressionFromText(CommonClassNames.JAVA_UTIL_OPTIONAL + ".of(" + optionalVariable.getName() + ".get())", trueValue);
    }
    if (ExpressionUtils.isReferenceTo(falseValue, optionalVariable)) {
      falseValue = factory.createExpressionFromText(CommonClassNames.JAVA_UTIL_OPTIONAL + ".empty()", falseValue);
    }
    String lambdaText = generateOptionalLambda(factory, ct, optionalVariable, trueValue);
    PsiLambdaExpression lambda = (PsiLambdaExpression)factory.createExpressionFromText(lambdaText, trueValue);
    return OptionalUtil.generateOptionalUnwrap(optionalVariable.getName(), lambda.getParameterList().getParameters()[0],
                                               (PsiExpression)lambda.getBody(), ct.markUnchanged(falseValue), targetType, true);
  }

  static boolean isSimpleOrUnchecked(PsiExpression expression) {
    return ExpressionUtils.isSimpleExpression(expression) || LambdaGenerationUtil.canBeUncheckedLambda(expression);
  }

  static class OptionalIsPresentFix implements LocalQuickFix {
    private final OptionalIsPresentCase myScenario;

    public OptionalIsPresentFix(OptionalIsPresentCase scenario) {
      myScenario = scenario;
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace Optional.isPresent() condition with functional style expression";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getStartElement();
      if (!(element instanceof PsiExpression)) return;
      PsiExpression condition = (PsiExpression)element;
      boolean invert = false;
      if (BoolUtils.isNegation(condition)) {
        condition = BoolUtils.getNegated(condition);
        invert = true;
      }
      PsiVariable optionalVariable = extractOptionalFromIfPresentCheck(condition);
      if (optionalVariable == null) return;
      PsiElement cond = PsiTreeUtil.getParentOfType(element, PsiIfStatement.class, PsiConditionalExpression.class);
      PsiElement thenElement;
      PsiElement elseElement;
      if(cond instanceof PsiIfStatement) {
        thenElement = extractThenStatement((PsiIfStatement)cond, invert);
        elseElement = extractElseStatement((PsiIfStatement)cond, invert);
      } else if(cond instanceof PsiConditionalExpression) {
        thenElement = invert ? ((PsiConditionalExpression)cond).getElseExpression() : ((PsiConditionalExpression)cond).getThenExpression();
        elseElement = invert ? ((PsiConditionalExpression)cond).getThenExpression() : ((PsiConditionalExpression)cond).getElseExpression();
      } else return;
      if (myScenario.getProblemType(optionalVariable, thenElement, elseElement) == ProblemType.NONE) return;
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      CommentTracker ct = new CommentTracker();
      String replacementText = myScenario.generateReplacement(factory, ct, optionalVariable, thenElement, elseElement);
      if (thenElement != null && !PsiTreeUtil.isAncestor(cond, thenElement, true)) ct.delete(thenElement);
      if (elseElement != null && !PsiTreeUtil.isAncestor(cond, elseElement, true)) ct.delete(elseElement);
      PsiElement result = ct.replaceAndRestoreComments(cond, replacementText);
      LambdaCanBeMethodReferenceInspection.replaceAllLambdasWithMethodReferences(result);
      PsiDiamondTypeUtil.removeRedundantTypeArguments(result);
      CodeStyleManager.getInstance(project).reformat(result);
    }
  }

  interface OptionalIsPresentCase {
    ProblemType getProblemType(PsiVariable optionalVariable, PsiElement trueElement, PsiElement falseElement);

    String generateReplacement(PsiElementFactory factory,
                               CommentTracker ct, PsiVariable optionalVariable,
                               PsiElement trueElement,
                               PsiElement falseElement);
  }

  static class ReturnCase implements OptionalIsPresentCase {
    @Override
    public ProblemType getProblemType(PsiVariable optionalVariable, PsiElement trueElement, PsiElement falseElement) {
      if (!(trueElement instanceof PsiReturnStatement) || !(falseElement instanceof PsiReturnStatement)) return ProblemType.NONE;
      PsiExpression falseValue = ((PsiReturnStatement)falseElement).getReturnValue();
      PsiExpression trueValue = ((PsiReturnStatement)trueElement).getReturnValue();
      if (!isSimpleOrUnchecked(falseValue)) return ProblemType.NONE;
      return getTypeByLambdaCandidate(optionalVariable, trueValue, falseValue);
    }

    @Override
    public String generateReplacement(PsiElementFactory factory,
                                      CommentTracker ct, PsiVariable optionalVariable,
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
  }

  static class AssignmentCase implements OptionalIsPresentCase {
    @Override
    public ProblemType getProblemType(PsiVariable optionalVariable, PsiElement trueElement, PsiElement falseElement) {
      PsiAssignmentExpression trueAssignment = ExpressionUtils.getAssignment(trueElement);
      PsiAssignmentExpression falseAssignment = ExpressionUtils.getAssignment(falseElement);
      if (trueAssignment == null || falseAssignment == null) return ProblemType.NONE;
      PsiExpression falseVal = falseAssignment.getRExpression();
      PsiExpression trueVal = trueAssignment.getRExpression();
      if (PsiEquivalenceUtil.areElementsEquivalent(trueAssignment.getLExpression(), falseAssignment.getLExpression()) &&
          isSimpleOrUnchecked(falseVal)) {
        return getTypeByLambdaCandidate(optionalVariable, trueVal, falseVal);
      }
      return ProblemType.NONE;
    }

    @Override
    public String generateReplacement(PsiElementFactory factory,
                                      CommentTracker ct, PsiVariable optionalVariable,
                                      PsiElement trueElement,
                                      PsiElement falseElement) {
      PsiAssignmentExpression trueAssignment = ExpressionUtils.getAssignment(trueElement);
      PsiAssignmentExpression falseAssignment = ExpressionUtils.getAssignment(falseElement);
      LOG.assertTrue(trueAssignment != null);
      LOG.assertTrue(falseAssignment != null);
      PsiExpression lValue = trueAssignment.getLExpression();
      PsiExpression trueValue = trueAssignment.getRExpression();
      PsiExpression falseValue = falseAssignment.getRExpression();
      LOG.assertTrue(falseValue != null);
      return lValue.getText() + " = " + generateOptionalUnwrap(factory, ct, optionalVariable, trueValue, falseValue, lValue.getType()) + ";";
    }
  }

  static class TernaryCase implements OptionalIsPresentCase {
    @Override
    public ProblemType getProblemType(PsiVariable optionalVariable, PsiElement trueElement, PsiElement falseElement) {
      if(!(trueElement instanceof PsiExpression) || !(falseElement instanceof PsiExpression)) return ProblemType.NONE;
      PsiExpression trueExpression = (PsiExpression)trueElement;
      PsiExpression falseExpression = (PsiExpression)falseElement;
      return (isSimpleOrUnchecked(falseExpression)) ?
             getTypeByLambdaCandidate(optionalVariable, trueExpression, falseExpression) : ProblemType.NONE;
    }

    @Override
    public String generateReplacement(PsiElementFactory factory,
                                      CommentTracker ct, PsiVariable optionalVariable,
                                      PsiElement trueElement,
                                      PsiElement falseElement) {
      PsiExpression ternary = PsiTreeUtil.getParentOfType(trueElement, PsiConditionalExpression.class);
      LOG.assertTrue(ternary != null);
      PsiExpression trueExpression = (PsiExpression)trueElement;
      PsiExpression falseExpression = (PsiExpression)falseElement;
      return generateOptionalUnwrap(factory, ct, optionalVariable, trueExpression, falseExpression, ternary.getType());
    }
  }

  static class ConsumerCase implements OptionalIsPresentCase {
    @Override
    public ProblemType getProblemType(PsiVariable optionalVariable, PsiElement trueElement, PsiElement falseElement) {
      if (falseElement != null && !(falseElement instanceof PsiEmptyStatement)) return ProblemType.NONE;
      if (!(trueElement instanceof PsiStatement)) return ProblemType.NONE;
      if (trueElement instanceof PsiExpressionStatement) {
        PsiExpression expression = ((PsiExpressionStatement)trueElement).getExpression();
        if(isOptionalGetCall(expression, optionalVariable)) return ProblemType.NONE;
        trueElement = expression;
      }
      return getTypeByLambdaCandidate(optionalVariable, trueElement, null);
    }

    @Override
    public String generateReplacement(PsiElementFactory factory,
                                      CommentTracker ct, PsiVariable optionalVariable,
                                      PsiElement trueElement,
                                      PsiElement falseElement) {
      return optionalVariable.getName() + ".ifPresent(" + generateOptionalLambda(factory, ct, optionalVariable, trueElement) + ");";
    }
  }
}
