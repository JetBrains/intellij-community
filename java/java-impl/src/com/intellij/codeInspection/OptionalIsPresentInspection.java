/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
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
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Tagir Valeev
 */
public class OptionalIsPresentInspection extends BaseJavaBatchLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance("#" + OptionalIsPresentInspection.class.getName());

  private static final OptionalIfPresentCase[] CASES = {
    new ReturnCase(),
    new AssignmentCase(),
    new ConsumerCase(),
    new TernaryCase()
  };

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
        for (OptionalIfPresentCase scenario : CASES) {
          if (scenario.isApplicable(optionalVariable, thenElement, elseElement)) {
            holder.registerProblem(condition, "Can be replaced with single expression in functional style",
                                   new OptionalIfPresentFix(scenario));
          }
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
        PsiElement nextElement = PsiTreeUtil.skipSiblingsForward(ifStatement, PsiComment.class, PsiWhiteSpace.class);
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
    if (!"get".equals(call.getMethodExpression().getReferenceName())) return false;
    PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
    if (!(qualifier instanceof PsiReferenceExpression)) return false;
    return ((PsiReferenceExpression)qualifier).isReferenceTo(variable);
  }

  @Contract("_, null, _ -> false")
  static boolean isOptionalLambdaCandidate(PsiVariable optionalVariable, PsiExpression lambdaCandidate, PsiExpression falseExpression) {
    if (lambdaCandidate == null) return false;
    if (ExpressionUtils.isReferenceTo(lambdaCandidate, optionalVariable) && OptionalUtil.isOptionalEmptyCall(falseExpression)) return true;
    if (!ExceptionUtil.getThrownCheckedExceptions(lambdaCandidate).isEmpty()) return false;
    Ref<Boolean> hasOptionalReference = new Ref<>(Boolean.FALSE);
    return PsiTreeUtil.processElements(lambdaCandidate, e -> {
      if (!(e instanceof PsiReferenceExpression)) return true;
      PsiElement element = ((PsiReferenceExpression)e).resolve();
      if (!(element instanceof PsiVariable)) return true;
      // Check that Optional variable is referenced only in context of get() call and other variables are effectively final
      if (element == optionalVariable) {
        hasOptionalReference.set(Boolean.TRUE);
        return isOptionalGetCall(e.getParent().getParent(), optionalVariable);
      }
      return HighlightControlFlowUtil.isEffectivelyFinal((PsiVariable)element, lambdaCandidate, null);
    }) && hasOptionalReference.get();
  }

  @NotNull
  static String generateOptionalLambda(PsiElementFactory factory, PsiVariable optionalVariable, PsiExpression trueValue) {
    PsiType type = optionalVariable.getType();
    JavaCodeStyleManager javaCodeStyleManager = JavaCodeStyleManager.getInstance(trueValue.getProject());
    SuggestedNameInfo info = javaCodeStyleManager.suggestVariableName(VariableKind.PARAMETER, null, null, type);
    if (info.names.length == 0) {
      info = javaCodeStyleManager.suggestVariableName(VariableKind.PARAMETER, "value", null, type);
    }
    String paramName = javaCodeStyleManager.suggestUniqueVariableName(info, trueValue, true).names[0];
    PsiElement copy = trueValue.copy();
    for (PsiElement getCall : PsiTreeUtil.collectElements(copy, e -> isOptionalGetCall(e, optionalVariable))) {
      PsiElement result = getCall.replace(factory.createIdentifier(paramName));
      if (copy == getCall) copy = result;
    }
    return paramName + "->" + copy.getText();
  }

  static String generateOptionalUnwrap(PsiElementFactory factory,
                                       PsiVariable optionalVariable,
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
    String lambdaText = generateOptionalLambda(factory, optionalVariable, trueValue);
    PsiLambdaExpression lambda = (PsiLambdaExpression)factory.createExpressionFromText(lambdaText, trueValue);
    return OptionalUtil.generateOptionalUnwrap(optionalVariable.getName(), lambda.getParameterList().getParameters()[0],
                                               (PsiExpression)lambda.getBody(), falseValue, targetType, true);
  }

  static class OptionalIfPresentFix implements LocalQuickFix {
    private final OptionalIfPresentCase myScenario;

    public OptionalIfPresentFix(OptionalIfPresentCase scenario) {
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
      if (!myScenario.isApplicable(optionalVariable, thenElement, elseElement)) return;
      if (!FileModificationService.getInstance().preparePsiElementForWrite(element.getContainingFile())) return;
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      PsiElement parent = cond.getParent();
      StreamEx.of(cond, thenElement, elseElement).nonNull()
        .flatCollection(st -> PsiTreeUtil.findChildrenOfType(st, PsiComment.class))
        .distinct()
        .forEach(comment -> {
          parent.addBefore(comment, cond);
          comment.delete();
        });
      String replacementText = myScenario.generateReplacement(factory, optionalVariable, thenElement, elseElement);
      if (thenElement != null && !PsiTreeUtil.isAncestor(cond, thenElement, true)) thenElement.delete();
      if (elseElement != null && !PsiTreeUtil.isAncestor(cond, elseElement, true)) elseElement.delete();
      PsiElement replacement = cond instanceof PsiExpression ?
                               factory.createExpressionFromText(replacementText, cond) :
                               factory.createStatementFromText(replacementText, cond);
      PsiElement result = cond.replace(replacement);
      LambdaCanBeMethodReferenceInspection.replaceAllLambdasWithMethodReferences(result);
      PsiDiamondTypeUtil.removeRedundantTypeArguments(result);
      CodeStyleManager.getInstance(project).reformat(result);
    }
  }

  interface OptionalIfPresentCase {
    boolean isApplicable(PsiVariable optionalVariable, PsiElement trueElement, PsiElement falseElement);

    String generateReplacement(PsiElementFactory factory,
                               PsiVariable optionalVariable,
                               PsiElement trueElement,
                               PsiElement falseElement);
  }

  static class ReturnCase implements OptionalIfPresentCase {
    @Override
    public boolean isApplicable(PsiVariable optionalVariable, PsiElement trueElement, PsiElement falseElement) {
      if (!(trueElement instanceof PsiReturnStatement) || !(falseElement instanceof PsiReturnStatement)) return false;
      PsiExpression falseValue = ((PsiReturnStatement)falseElement).getReturnValue();
      if (!ExpressionUtils.isSimpleExpression(falseValue) &&
          !LambdaGenerationUtil.canBeUncheckedLambda(falseValue)) return false;
      PsiExpression trueValue = ((PsiReturnStatement)trueElement).getReturnValue();
      return isOptionalLambdaCandidate(optionalVariable, trueValue, falseValue);
    }

    @Override
    public String generateReplacement(PsiElementFactory factory,
                                      PsiVariable optionalVariable,
                                      PsiElement trueElement,
                                      PsiElement falseElement) {
      PsiExpression trueValue = ((PsiReturnStatement)trueElement).getReturnValue();
      PsiExpression falseValue = ((PsiReturnStatement)falseElement).getReturnValue();
      LOG.assertTrue(trueValue != null);
      LOG.assertTrue(falseValue != null);
      return "return " +
             generateOptionalUnwrap(factory, optionalVariable, trueValue, falseValue, PsiTypesUtil.getMethodReturnType(trueElement)) +
             ";";
    }
  }

  static class AssignmentCase implements OptionalIfPresentCase {
    @Override
    public boolean isApplicable(PsiVariable optionalVariable, PsiElement trueElement, PsiElement falseElement) {
      PsiAssignmentExpression trueAssignment = ExpressionUtils.getAssignment(trueElement);
      PsiAssignmentExpression falseAssignment = ExpressionUtils.getAssignment(falseElement);
      if (trueAssignment == null ||
          falseAssignment == null ||
          !EquivalenceChecker.getCanonicalPsiEquivalence()
            .expressionsAreEquivalent(trueAssignment.getLExpression(), falseAssignment.getLExpression()) ||
          !isOptionalLambdaCandidate(optionalVariable, trueAssignment.getRExpression(), falseAssignment.getLExpression())) {
        return false;
      }
      return ExpressionUtils.isSimpleExpression(falseAssignment.getRExpression()) ||
             LambdaGenerationUtil.canBeUncheckedLambda(falseAssignment.getRExpression());
    }

    @Override
    public String generateReplacement(PsiElementFactory factory,
                                      PsiVariable optionalVariable,
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
      return lValue.getText() + " = " + generateOptionalUnwrap(factory, optionalVariable, trueValue, falseValue, lValue.getType()) + ";";
    }
  }

  static class TernaryCase implements OptionalIfPresentCase {
    @Override
    public boolean isApplicable(PsiVariable optionalVariable, PsiElement trueElement, PsiElement falseElement) {
      if(!(trueElement instanceof PsiExpression) || !(falseElement instanceof PsiExpression)) return false;
      PsiExpression trueExpression = (PsiExpression)trueElement;
      PsiExpression falseExpression = (PsiExpression)falseElement;
      return isOptionalLambdaCandidate(optionalVariable, trueExpression, falseExpression) &&
             (ExpressionUtils.isSimpleExpression(falseExpression) || LambdaGenerationUtil.canBeUncheckedLambda(falseExpression));
    }

    @Override
    public String generateReplacement(PsiElementFactory factory,
                                      PsiVariable optionalVariable,
                                      PsiElement trueElement,
                                      PsiElement falseElement) {
      PsiExpression ternary = PsiTreeUtil.getParentOfType(trueElement, PsiConditionalExpression.class);
      LOG.assertTrue(ternary != null);
      PsiExpression trueExpression = (PsiExpression)trueElement;
      PsiExpression falseExpression = (PsiExpression)falseElement;
      return generateOptionalUnwrap(factory, optionalVariable, trueExpression, falseExpression, ternary.getType());
    }
  }

  static class ConsumerCase implements OptionalIfPresentCase {
    @Override
    public boolean isApplicable(PsiVariable optionalVariable, PsiElement trueElement, PsiElement falseElement) {
      if (falseElement != null && !(falseElement instanceof PsiEmptyStatement)) return false;
      if (!(trueElement instanceof PsiExpressionStatement)) return false;
      PsiExpression expression = ((PsiExpressionStatement)trueElement).getExpression();
      return isOptionalLambdaCandidate(optionalVariable, expression, null);
    }

    @Override
    public String generateReplacement(PsiElementFactory factory,
                                      PsiVariable optionalVariable,
                                      PsiElement trueElement,
                                      PsiElement falseElement) {
      PsiExpression expression = ((PsiExpressionStatement)trueElement).getExpression();
      return optionalVariable.getName() + ".ifPresent(" + generateOptionalLambda(factory, optionalVariable, expression) + ");";
    }
  }
}
