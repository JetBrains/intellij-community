// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.highlighting;

import com.intellij.core.JavaPsiBundle;
import com.intellij.java.codeserver.highlighting.errors.JavaErrorKinds;
import com.intellij.java.codeserver.highlighting.errors.JavaIncompatibleTypeErrorContext;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.ControlFlow;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.util.JavaPsiSwitchUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class SwitchChecker {
  private final @NotNull JavaErrorVisitor myVisitor;

  SwitchChecker(@NotNull JavaErrorVisitor visitor) { myVisitor = visitor; }

  void checkSwitchExpressionReturnTypeCompatible(@NotNull PsiSwitchExpression switchExpression) {
    if (!PsiPolyExpressionUtil.isPolyExpression(switchExpression)) {
      return;
    }
    PsiType switchExpressionType = switchExpression.getType();
    if (switchExpressionType != null) {
      for (PsiExpression expression : PsiUtil.getSwitchResultExpressions(switchExpression)) {
        PsiType expressionType = expression.getType();
        if (expressionType != null && !TypeConversionUtil.areTypesAssignmentCompatible(switchExpressionType, expression)) {
          myVisitor.report(JavaErrorKinds.SWITCH_EXPRESSION_INCOMPATIBLE_TYPE.create(
            expression, new JavaIncompatibleTypeErrorContext(switchExpressionType, expressionType)));
        }
      }

      if (PsiTypes.voidType().equals(switchExpressionType)) {
        myVisitor.report(JavaErrorKinds.SWITCH_EXPRESSION_CANNOT_BE_VOID.create(switchExpression));
      }
    }
  }

  void checkSwitchBlockStatements(@NotNull PsiSwitchBlock block) {
    PsiCodeBlock body = block.getBody();
    if (body == null) return;
    PsiElement first = PsiTreeUtil.skipWhitespacesAndCommentsForward(body.getLBrace());
    if (first != null && !(first instanceof PsiSwitchLabelStatementBase) && !PsiUtil.isJavaToken(first, JavaTokenType.RBRACE)) {
      myVisitor.report(JavaErrorKinds.SWITCH_LABEL_EXPECTED.create(first));
    }
    PsiElement element = first;
    PsiStatement alien = null;
    boolean classicLabels = false;
    boolean enhancedLabels = false;
    boolean levelChecked = false;
    while (element != null && !PsiUtil.isJavaToken(element, JavaTokenType.RBRACE)) {
      if (element instanceof PsiSwitchLabeledRuleStatement) {
        if (!levelChecked) {
          myVisitor.checkFeature(element, JavaFeature.ENHANCED_SWITCH);
          if (myVisitor.hasErrorResults()) return;
          levelChecked = true;
        }
        if (classicLabels) {
          alien = (PsiStatement)element;
          break;
        }
        enhancedLabels = true;
      }
      else if (element instanceof PsiStatement statement) {
        if (enhancedLabels) {
          //let's not highlight twice
          if (statement instanceof PsiSwitchLabelStatement labelStatement &&
              labelStatement.getChildren().length != 0 &&
              labelStatement.getChildren()[labelStatement.getChildren().length - 1] instanceof PsiErrorElement errorElement &&
              errorElement.getErrorDescription().startsWith(JavaPsiBundle.message("expected.colon.or.arrow"))) {
            break;
          }
          alien = statement;
          break;
        }
        classicLabels = true;
      }

      if (!levelChecked && element instanceof PsiSwitchLabelStatementBase label) {
        @Nullable PsiCaseLabelElementList values = label.getCaseLabelElementList();
        if (values != null && values.getElementCount() > 1) {
          myVisitor.checkFeature(values, JavaFeature.ENHANCED_SWITCH);
          if (myVisitor.hasErrorResults()) return;
          levelChecked = true;
        }
      }

      element = PsiTreeUtil.skipWhitespacesAndCommentsForward(element);
    }
    if (alien == null) return;
    if (enhancedLabels && !(alien instanceof PsiSwitchLabelStatementBase)) {
      myVisitor.report(JavaErrorKinds.SWITCH_LABEL_EXPECTED.create(alien));
      return;
    }
    myVisitor.report(JavaErrorKinds.SWITCH_DIFFERENT_CASE_KINDS.create(alien));
  }

  void checkSwitchSelectorType(@NotNull PsiSwitchBlock block) {
    PsiExpression selector = block.getExpression();
    if (selector == null) return;
    PsiType selectorType = selector.getType();
    if (selectorType == null) return;
    JavaPsiSwitchUtil.SelectorKind kind = JavaPsiSwitchUtil.getSwitchSelectorKind(selectorType);

    JavaFeature requiredFeature = kind.getFeature();

    if ((kind == JavaPsiSwitchUtil.SelectorKind.INVALID || requiredFeature != null && !myVisitor.isApplicable(requiredFeature)) &&
        !PsiTreeUtil.hasErrorElements(block)) {
      myVisitor.report(JavaErrorKinds.SWITCH_SELECTOR_TYPE_INVALID.create(selector, kind));
    }
    PsiClass member = PsiUtil.resolveClassInClassTypeOnly(selectorType);
    if (member != null && !PsiUtil.isAccessible(member.getProject(), member, selector, null)) {
      myVisitor.report(JavaErrorKinds.TYPE_INACCESSIBLE.create(selector, member));
    }
  }

  void checkSwitchExpressionHasResult(@NotNull PsiSwitchExpression switchExpression) {
    PsiCodeBlock switchBody = switchExpression.getBody();
    if (switchBody != null) {
      PsiStatement lastStatement = PsiTreeUtil.getPrevSiblingOfType(switchBody.getRBrace(), PsiStatement.class);
      boolean hasResult = false;
      if (lastStatement instanceof PsiSwitchLabeledRuleStatement rule) {
        boolean reported = false;
        for (;
             rule != null;
             rule = PsiTreeUtil.getPrevSiblingOfType(rule, PsiSwitchLabeledRuleStatement.class)) {
          PsiStatement ruleBody = rule.getBody();
          if (ruleBody instanceof PsiExpressionStatement) {
            hasResult = true;
          }
          // the expression and throw statements are fine, only the block statement could be an issue
          // 15.28.1 If the switch block consists of switch rules, then any switch rule block cannot complete normally
          if (ruleBody instanceof PsiBlockStatement) {
            ControlFlow flow = ControlFlowChecker.getControlFlow(ruleBody);
            if (flow != null && ControlFlowUtil.canCompleteNormally(flow, 0, flow.getSize())) {
              myVisitor.report(JavaErrorKinds.SWITCH_RULE_SHOULD_PRODUCE_RESULT.create(rule));
              reported = true;
            }
            else if (!hasResult && hasYield(switchExpression, ruleBody)) {
              hasResult = true;
            }
          }
        }
        if (reported) {
          return;
        }
      }
      else {
        // previous statements may have no result as well, but in that case they fall through to the last one, which needs to be checked anyway
        if (lastStatement != null) {
          boolean canCompleteNormally;
          if (lastStatement instanceof PsiSwitchLabelStatement) {
            canCompleteNormally = true;
          } else {
            ControlFlow flow = ControlFlowChecker.getControlFlow(lastStatement);
            canCompleteNormally = flow != null && ControlFlowUtil.canCompleteNormally(flow, 0, flow.getSize());
          }
          if (canCompleteNormally) {
            myVisitor.report(JavaErrorKinds.SWITCH_EXPRESSION_SHOULD_PRODUCE_RESULT.create(switchExpression));
            return;
          }
        }
        hasResult = hasYield(switchExpression, switchBody);
      }
      if (!hasResult) {
        myVisitor.report(JavaErrorKinds.SWITCH_EXPRESSION_NO_RESULT.create(switchExpression));
      }
    }
  }

  private static boolean hasYield(@NotNull PsiSwitchExpression switchExpression, @NotNull PsiElement scope) {
    class YieldFinder extends JavaRecursiveElementWalkingVisitor {
      private boolean hasYield;

      @Override
      public void visitYieldStatement(@NotNull PsiYieldStatement statement) {
        if (statement.findEnclosingExpression() == switchExpression) {
          hasYield = true;
          stopWalking();
        }
      }

      // do not go inside to save time: declarations cannot contain yield that points to outer switch expression
      @Override
      public void visitDeclarationStatement(@NotNull PsiDeclarationStatement statement) {}

      // do not go inside to save time: expressions cannot contain yield that points to outer switch expression
      @Override
      public void visitExpression(@NotNull PsiExpression expression) {}
    }
    YieldFinder finder = new YieldFinder();
    scope.accept(finder);
    return finder.hasYield;
  }

  void checkLocalClassReferencedFromAnotherSwitchBranch(@NotNull PsiJavaCodeReferenceElement ref, @NotNull PsiClass aClass) {
    if (!(aClass.getParent() instanceof PsiDeclarationStatement declarationStatement) ||
        !(declarationStatement.getParent() instanceof PsiCodeBlock codeBlock) ||
        !(codeBlock.getParent() instanceof PsiSwitchBlock)) {
      return;
    }
    boolean classSeen = false;
    for (PsiStatement statement : codeBlock.getStatements()) {
      if (classSeen) {
        if (PsiTreeUtil.isAncestor(statement, ref, true)) break;
        if (statement instanceof PsiSwitchLabelStatement) {
          myVisitor.report(JavaErrorKinds.REFERENCE_LOCAL_CLASS_OTHER_SWITCH_BRANCH.create(ref, aClass));
          return;
        }
      }
      else if (statement == declarationStatement) {
        classSeen = true;
      }
    }
  }

  void checkYieldOutsideSwitchExpression(@NotNull PsiYieldStatement statement) {
    if (statement.findEnclosingExpression() == null) {
      myVisitor.report(JavaErrorKinds.YIELD_UNEXPECTED.create(statement));
    }
  }

  void checkCaseStatement(@NotNull PsiSwitchLabelStatementBase statement) {
    PsiSwitchBlock switchBlock = statement.getEnclosingSwitchBlock();
    if (switchBlock == null) {
      myVisitor.report(JavaErrorKinds.STATEMENT_CASE_OUTSIDE_SWITCH.create(statement));
    }
  }

  void checkGuard(@NotNull PsiSwitchLabelStatementBase statement) {
    PsiExpression guardingExpr = statement.getGuardExpression();
    if (guardingExpr == null) return;
    myVisitor.checkFeature(guardingExpr, JavaFeature.PATTERN_GUARDS_AND_RECORD_PATTERNS);
    if (myVisitor.hasErrorResults()) return;
    PsiCaseLabelElementList list = statement.getCaseLabelElementList();
    if (list != null) {
      if (!ContainerUtil.exists(list.getElements(), e -> e instanceof PsiPattern)) {
        myVisitor.report(JavaErrorKinds.GUARD_MISPLACED.create(guardingExpr));
        return;
      }
    }
    if (!TypeConversionUtil.isBooleanType(guardingExpr.getType())) {
      myVisitor.report(JavaErrorKinds.TYPE_INCOMPATIBLE.create(
        guardingExpr, new JavaIncompatibleTypeErrorContext(PsiTypes.booleanType(), guardingExpr.getType())));
      return;
    }
    Object constVal = JavaPsiFacade.getInstance(myVisitor.project()).getConstantEvaluationHelper().computeConstantExpression(guardingExpr);
    if (Boolean.FALSE.equals(constVal)) {
      myVisitor.report(JavaErrorKinds.GUARD_EVALUATED_TO_FALSE.create(guardingExpr));
    }
  }

  void checkYieldExpressionType(@NotNull PsiExpression expression) {
    if (PsiTypes.voidType().equals(expression.getType())) {
      myVisitor.report(JavaErrorKinds.YIELD_VOID.create(expression));
    }
  }

}
