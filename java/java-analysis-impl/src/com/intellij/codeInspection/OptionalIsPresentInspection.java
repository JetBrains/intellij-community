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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTreeUtil;
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

import java.util.Collection;

/**
 * @author Tagir Valeev
 */
public class OptionalIsPresentInspection extends BaseJavaBatchLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance("#" + OptionalIsPresentInspection.class.getName());

  private static final OptionalIfPresentCase[] CASES = {
    new ReturnCase(),
    new AssignmentCase(),
    new ConsumerCase()
  };

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel8OrHigher(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitIfStatement(PsiIfStatement statement) {
        super.visitIfStatement(statement);
        PsiExpression condition = PsiUtil.skipParenthesizedExprDown(statement.getCondition());
        if(condition == null) return;
        boolean invert = false;
        PsiExpression strippedCondition = condition;
        if(BoolUtils.isNegation(condition)) {
          strippedCondition = BoolUtils.getNegated(condition);
          invert = true;
        }
        PsiVariable optionalVariable = extractOptionalFromIfPresentCheck(strippedCondition);
        if(optionalVariable == null) return;
        PsiStatement thenStatement = extractThenStatement(statement, invert);
        PsiStatement elseStatement = extractElseStatement(statement, invert);
        for(OptionalIfPresentCase scenario : CASES) {
          String replacementName = scenario.getReplacementName(optionalVariable, thenStatement, elseStatement);
          if(replacementName != null) {
            holder.registerProblem(condition, "Can be replaced with "+replacementName,
                                   new OptionalIfPresentFix(scenario, replacementName));
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
    if(invert) return extractElseStatement(ifStatement, false);
    return ControlFlowUtils.stripBraces(ifStatement.getThenBranch());
  }

  private static PsiStatement extractElseStatement(PsiIfStatement ifStatement, boolean invert) {
    if(invert) return extractThenStatement(ifStatement, false);
    PsiStatement statement = ControlFlowUtils.stripBraces(ifStatement.getElseBranch());
    if(statement == null) {
      PsiStatement thenStatement = extractThenStatement(ifStatement, false);
      if(thenStatement instanceof PsiReturnStatement) {
        PsiElement nextElement = PsiTreeUtil.skipSiblingsForward(ifStatement, PsiComment.class, PsiWhiteSpace.class);
        if(nextElement instanceof PsiStatement) {
          statement = ControlFlowUtils.stripBraces((PsiStatement)nextElement);
        }
      }
    }
    return statement;
  }

  @Contract("null -> null")
  static PsiVariable extractOptionalFromIfPresentCheck(PsiExpression expression) {
    if(!(expression instanceof PsiMethodCallExpression)) return null;
    PsiMethodCallExpression call = (PsiMethodCallExpression)expression;
    if(call.getArgumentList().getExpressions().length != 0) return null;
    if(!"isPresent".equals(call.getMethodExpression().getReferenceName())) return null;
    PsiMethod method = call.resolveMethod();
    if(method == null) return null;
    PsiClass containingClass = method.getContainingClass();
    if (containingClass == null || !CommonClassNames.JAVA_UTIL_OPTIONAL.equals(containingClass.getQualifiedName())) return null;
    PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
    if(!(qualifier instanceof PsiReferenceExpression)) return null;
    PsiElement element = ((PsiReferenceExpression)qualifier).resolve();
    if (!(element instanceof PsiVariable) || isRaw((PsiVariable)element)) return null;
    return (PsiVariable)element;
  }

  @Contract("null, _ -> false")
  static boolean isOptionalGetCall(PsiElement element, PsiVariable variable) {
    if(!(element instanceof PsiMethodCallExpression)) return false;
    PsiMethodCallExpression call = (PsiMethodCallExpression)element;
    if(call.getArgumentList().getExpressions().length != 0) return false;
    if(!"get".equals(call.getMethodExpression().getReferenceName())) return false;
    PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
    if(!(qualifier instanceof PsiReferenceExpression)) return false;
    return ((PsiReferenceExpression)qualifier).resolve() == variable;
  }

  @Contract("null, _ -> false")
  static boolean isOptionalLambdaCandidate(PsiExpression lambdaCandidate, PsiVariable optionalVariable) {
    if(lambdaCandidate == null) return false;
    if(!ExceptionUtil.getThrownCheckedExceptions(new PsiElement[] {lambdaCandidate}).isEmpty()) return false;
    return PsiTreeUtil.processElements(lambdaCandidate, e -> {
      if (!(e instanceof PsiReferenceExpression)) return true;
      PsiElement element = ((PsiReferenceExpression)e).resolve();
      if(!(element instanceof PsiVariable)) return true;
      // Check that Optional variable is referenced only in context of get() call and other variables are effectively final
      return element == optionalVariable
             ? isOptionalGetCall(e.getParent().getParent(), optionalVariable)
             : HighlightControlFlowUtil.isEffectivelyFinal((PsiVariable)element, lambdaCandidate, null);
    });
  }

  @Contract("null -> false")
  public static boolean isVoidLambdaCandidate(PsiExpression lambdaCandidate) {
    if(lambdaCandidate == null) return false;
    if(!ExceptionUtil.getThrownCheckedExceptions(new PsiElement[] {lambdaCandidate}).isEmpty()) return false;
    return PsiTreeUtil.processElements(lambdaCandidate, e -> {
      if (!(e instanceof PsiReferenceExpression)) return true;
      PsiElement element = ((PsiReferenceExpression)e).resolve();
      return !(element instanceof PsiVariable) ||
             HighlightControlFlowUtil.isEffectivelyFinal((PsiVariable)element, lambdaCandidate, null);
    });
  }

  static String getComments(PsiStatement statement) {
    Collection<PsiComment> comments = PsiTreeUtil.collectElementsOfType(statement, PsiComment.class);
    return StreamEx.of(comments)
      .filter(c -> c.getParent() == statement ||
                   (statement instanceof PsiExpressionStatement && c.getParent() == ((PsiExpressionStatement)statement).getExpression()))
      .flatMap(c -> StreamEx.of(c.getPrevSibling(), c, c.getNextSibling())) // add both siblings for every comment
      .filter(e -> e instanceof PsiComment || e instanceof PsiWhiteSpace) // select only comments and whitespace
      .distinct()
      .map(PsiElement::getText).joining("");
  }

  @NotNull
  static String generateMapIfNeeded(PsiElementFactory factory,
                                    PsiVariable optionalVariable,
                                    PsiStatement trueStatement,
                                    PsiExpression trueValue) {
    String trueComments = getComments(trueStatement);
    if(isOptionalGetCall(trueValue, optionalVariable)) {
      return trueComments + optionalVariable.getName();
    } else {
      return optionalVariable.getName() + ".map(" + trueComments + generateOptionalLambda(factory, optionalVariable, trueValue) + ")";
    }
  }

  @NotNull
  static String generateOptionalLambda(PsiElementFactory factory, PsiVariable optionalVariable, PsiExpression trueValue) {
    PsiType type = optionalVariable.getType();
    JavaCodeStyleManager javaCodeStyleManager = JavaCodeStyleManager.getInstance(trueValue.getProject());
    SuggestedNameInfo info = javaCodeStyleManager.suggestVariableName(VariableKind.PARAMETER, null, null, type);
    if(info.names.length == 0)
      info = javaCodeStyleManager.suggestVariableName(VariableKind.PARAMETER, "value", null, type);
    String paramName = javaCodeStyleManager.suggestUniqueVariableName(info, trueValue, true).names[0];
    PsiElement copy = trueValue.copy();
    for (PsiElement getCall : PsiTreeUtil.collectElements(copy, e -> isOptionalGetCall(e, optionalVariable))) {
      getCall.replace(factory.createIdentifier(paramName));
    }
    return paramName + "->" + copy.getText();
  }

  static class OptionalIfPresentFix implements LocalQuickFix {
    private final OptionalIfPresentCase myScenario;
    private final String myReplacementName;

    public OptionalIfPresentFix(OptionalIfPresentCase scenario, String replacementName) {
      myScenario = scenario;
      myReplacementName = replacementName;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return "Replace Optional.isPresent() condition with "+myReplacementName;
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
      if(!(element instanceof PsiExpression)) return;
      PsiIfStatement statement = PsiTreeUtil.getParentOfType(element, PsiIfStatement.class);
      if(statement == null) return;
      PsiExpression condition = (PsiExpression)element;
      boolean invert = false;
      if(BoolUtils.isNegation(condition)) {
        condition = BoolUtils.getNegated(condition);
        invert = true;
      }
      PsiVariable optionalVariable = extractOptionalFromIfPresentCheck(condition);
      if(optionalVariable == null) return;
      PsiStatement thenStatement = extractThenStatement(statement, invert);
      PsiStatement elseStatement = extractElseStatement(statement, invert);
      if (!myScenario.isApplicable(optionalVariable, thenStatement, elseStatement)) return;
      if (!FileModificationService.getInstance().preparePsiElementForWrite(element.getContainingFile())) return;
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      String replacement = myScenario.generateReplacement(factory, optionalVariable, thenStatement, elseStatement);
      final PsiElement parent = statement.getParent();
      for (PsiElement comment : PsiTreeUtil.findChildrenOfType(statement, PsiComment.class)) {
        // Comments inside then/else statements should be handled by scenario
        if((thenStatement == null || !PsiTreeUtil.isAncestor(thenStatement, comment, true)) &&
           (elseStatement == null || !PsiTreeUtil.isAncestor(elseStatement, comment, true))) {
          parent.addBefore(comment, statement);
        }
      }
      if(thenStatement != null && !PsiTreeUtil.isAncestor(statement, thenStatement, true)) thenStatement.delete();
      if(elseStatement != null && !PsiTreeUtil.isAncestor(statement, elseStatement, true)) elseStatement.delete();
      PsiElement result = statement.replace(factory.createStatementFromText(replacement, statement));
      LambdaCanBeMethodReferenceInspection.replaceAllLambdasWithMethodReferences(result);
      CodeStyleManager.getInstance(project).reformat(result);
    }
  }

  interface OptionalIfPresentCase {
    String getReplacementName(PsiVariable optionalVariable, PsiStatement trueStatement, PsiStatement falseStatement);

    default boolean isApplicable(PsiVariable optionalVariable, PsiStatement trueStatement, PsiStatement falseStatement) {
      return getReplacementName(optionalVariable, trueStatement, falseStatement) != null;
    }

    String generateReplacement(PsiElementFactory factory,
                               PsiVariable optionalVariable,
                               PsiStatement trueStatement,
                               PsiStatement falseStatement);
  }

  static class ReturnCase implements OptionalIfPresentCase {
    @Override
    public String getReplacementName(PsiVariable optionalVariable, PsiStatement trueStatement, PsiStatement falseStatement) {
      if (!(trueStatement instanceof PsiReturnStatement) || !(falseStatement instanceof PsiReturnStatement)) return null;
      PsiExpression falseValue = ((PsiReturnStatement)falseStatement).getReturnValue();
      if(!ExpressionUtils.isSimpleExpression(falseValue)) return null;
      PsiExpression trueValue = ((PsiReturnStatement)trueStatement).getReturnValue();
      if(!isOptionalLambdaCandidate(trueValue, optionalVariable)) return null;
      return isOptionalGetCall(trueValue, optionalVariable) ? "orElse()" : "map().orElse()";
    }

    @Override
    public String generateReplacement(PsiElementFactory factory,
                                      PsiVariable optionalVariable,
                                      PsiStatement trueStatement,
                                      PsiStatement falseStatement) {
      PsiExpression trueValue = ((PsiReturnStatement)trueStatement).getReturnValue();
      PsiExpression falseValue = ((PsiReturnStatement)falseStatement).getReturnValue();
      LOG.assertTrue(trueValue != null);
      LOG.assertTrue(falseValue != null);
      String trueBranch = generateMapIfNeeded(factory, optionalVariable, trueStatement, trueValue);
      return "return " + trueBranch + ".orElse(" + getComments(falseStatement) + falseValue.getText() + ");";
    }
  }

  static class AssignmentCase implements OptionalIfPresentCase {

    @Override
    public String getReplacementName(PsiVariable optionalVariable, PsiStatement trueStatement, PsiStatement falseStatement) {
      PsiAssignmentExpression trueAssignment = ExpressionUtils.getAssignment(trueStatement);
      PsiAssignmentExpression falseAssignment = ExpressionUtils.getAssignment(falseStatement);
      if (trueAssignment == null ||
          falseAssignment == null ||
          !EquivalenceChecker.getCanonicalPsiEquivalence()
            .expressionsAreEquivalent(trueAssignment.getLExpression(), falseAssignment.getLExpression()) ||
          !isOptionalLambdaCandidate(trueAssignment.getRExpression(), optionalVariable)) {
        return null;
      }
      String mapPart = isOptionalGetCall(trueAssignment.getRExpression(), optionalVariable) ? "" : "map().";
      if(ExpressionUtils.isSimpleExpression(falseAssignment.getRExpression())) {
        return mapPart + "orElse()";
      }
      if(isVoidLambdaCandidate(falseAssignment.getRExpression())) {
        return mapPart + "orElseGet()";
      }
      return null;
    }

    @Override
    public String generateReplacement(PsiElementFactory factory,
                                      PsiVariable optionalVariable,
                                      PsiStatement trueStatement,
                                      PsiStatement falseStatement) {
      PsiAssignmentExpression trueAssignment = ExpressionUtils.getAssignment(trueStatement);
      PsiAssignmentExpression falseAssignment = ExpressionUtils.getAssignment(falseStatement);
      LOG.assertTrue(trueAssignment != null);
      LOG.assertTrue(falseAssignment != null);
      PsiExpression lValue = trueAssignment.getLExpression();
      PsiExpression trueValue = trueAssignment.getRExpression();
      PsiExpression falseValue = falseAssignment.getRExpression();
      LOG.assertTrue(falseValue != null);
      String trueBranch = generateMapIfNeeded(factory, optionalVariable, trueStatement, trueValue);
      String falseBranch;
      if(ExpressionUtils.isSimpleExpression(falseValue)) {
        falseBranch = "orElse(" + getComments(falseStatement) + falseValue.getText() + ")";
      } else {
        falseBranch = "orElseGet(" + getComments(falseStatement) + "() -> " + falseValue.getText() + ")";
      }
      return lValue.getText() + " = " + trueBranch + "." + falseBranch + ";";
    }
  }

  static class ConsumerCase implements OptionalIfPresentCase {

    @Override
    public String getReplacementName(PsiVariable optionalVariable, PsiStatement trueStatement, PsiStatement falseStatement) {
      if(falseStatement != null && !(falseStatement instanceof PsiEmptyStatement)) return null;
      if(!(trueStatement instanceof PsiExpressionStatement)) return null;
      PsiExpression expression = ((PsiExpressionStatement)trueStatement).getExpression();
      if(!isOptionalLambdaCandidate(expression, optionalVariable)) return null;
      return "ifPresent()";
    }

    @Override
    public String generateReplacement(PsiElementFactory factory,
                                      PsiVariable optionalVariable,
                                      PsiStatement trueStatement,
                                      PsiStatement falseStatement) {
      PsiExpression expression = ((PsiExpressionStatement)trueStatement).getExpression();
      return optionalVariable.getName() + ".ifPresent(" + generateOptionalLambda(factory, optionalVariable, expression) + ");";
    }
  }
}
