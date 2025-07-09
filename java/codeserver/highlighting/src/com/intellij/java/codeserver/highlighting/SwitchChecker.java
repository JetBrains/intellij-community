// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.highlighting;

import com.intellij.codeInsight.ExpressionUtil;
import com.intellij.core.JavaPsiBundle;
import com.intellij.java.codeserver.core.JavaPsiSwitchUtil;
import com.intellij.java.codeserver.highlighting.errors.JavaErrorKinds;
import com.intellij.java.codeserver.highlighting.errors.JavaIncompatibleTypeErrorContext;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.ControlFlow;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.psi.impl.IncompleteModelUtil;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.util.*;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.java.codeserver.core.JavaPatternExhaustivenessUtil.hasExhaustivenessError;

final class SwitchChecker {
  private final @NotNull JavaErrorVisitor myVisitor;

  SwitchChecker(@NotNull JavaErrorVisitor visitor) { myVisitor = visitor; }

  void checkSwitchBlock(@NotNull PsiSwitchBlock block) {
    if (!myVisitor.hasErrorResults()) checkSwitchBlockStatements(block);
    if (!myVisitor.hasErrorResults()) checkSwitchSelectorType(block);
    if (!myVisitor.hasErrorResults()) checkLabelSelectorCompatibility(block);
    if (!myVisitor.hasErrorResults()) checkDuplicates(block);
    if (!myVisitor.hasErrorResults()) checkFallthroughLegality(block);
    if (!myVisitor.hasErrorResults()) checkDominance(block);
    if (!myVisitor.hasErrorResults()) checkNoDefaultBranchAllowed(block);
    if (!myVisitor.hasErrorResults()) checkExhaustiveness(block);
  }

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

  private void checkSwitchBlockStatements(@NotNull PsiSwitchBlock block) {
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

  private void checkSwitchSelectorType(@NotNull PsiSwitchBlock block) {
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
      // If there are no cases, empty switch should be reported
      if (!hasResult && PsiTreeUtil.getChildOfType(switchBody, PsiSwitchLabelStatementBase.class) != null) {
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
      myVisitor.reportIncompatibleType(PsiTypes.booleanType(), guardingExpr.getType(), guardingExpr);
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

  private void checkLabelSelectorCompatibility(@NotNull PsiSwitchBlock block) {
    PsiCodeBlock body = block.getBody();
    if (body == null) return;
    PsiExpression selector = block.getExpression();
    if (selector == null) return;
    PsiType selectorType = selector.getType();
    if (selectorType == null) return;
    boolean patterns = myVisitor.isApplicable(JavaFeature.PATTERNS_IN_SWITCH);

    for (PsiStatement st : body.getStatements()) {
      if (!(st instanceof PsiSwitchLabelStatementBase labelStatement)) continue;
      if (labelStatement.isDefaultCase()) continue;
      PsiCaseLabelElementList labelElementList = labelStatement.getCaseLabelElementList();
      if (labelElementList == null) continue;
      for (PsiCaseLabelElement label : labelElementList.getElements()) {
        if (!(label instanceof PsiParenthesizedExpression) && ExpressionUtil.isNullLiteral(label)) {
          if (selectorType instanceof PsiPrimitiveType && !PsiTypes.nullType().equals(selectorType)) {
            myVisitor.report(JavaErrorKinds.SWITCH_NULL_TYPE_INCOMPATIBLE.create(label, selectorType));
          }
          continue;
        }
        if (label instanceof PsiExpression expr) {
          if (selectorType.equals(PsiTypes.nullType())) {
            myVisitor.reportIncompatibleType(selectorType, expr.getType(), expr);
            continue;
          }
          if (label instanceof PsiReferenceExpression ref) {
            String enumConstName = evaluateEnumConstantName(ref);
            if (enumConstName != null) {
              if (!myVisitor.isApplicable(JavaFeature.ENUM_QUALIFIED_NAME_IN_SWITCH) && ref.getQualifier() != null) {
                myVisitor.report(JavaErrorKinds.SWITCH_LABEL_QUALIFIED_ENUM.create(ref));
              }
              continue;
            }
          }
          if (!myVisitor.myExpressionChecker.checkAssignability(selectorType, expr.getType(), expr, expr)) continue;
        }
        if (patterns) {
          checkLabelAndSelectorCompatibilityPattern(label, selectorType);
        }
        else {
          checkLabelAndSelectorCompatibility(label, labelElementList, selectorType);
        }
      }
    }
  }

  static @Nullable String evaluateEnumConstantName(@NotNull PsiReferenceExpression expr) {
    return expr.resolve() instanceof PsiEnumConstant enumConstant ? enumConstant.getName() : null;
  }

  private static @Nullable Object evaluateConstant(@NotNull PsiCaseLabelElement constant) {
    return JavaPsiFacade.getInstance(constant.getProject()).getConstantEvaluationHelper().computeConstantExpression(constant, false);
  }

  private void checkLabelAndSelectorCompatibility(@NotNull PsiCaseLabelElement label,
                                                  @NotNull PsiCaseLabelElementList labelElementList,
                                                  @NotNull PsiType selectorType) {
    if (label instanceof PsiExpression expr) {
      Object value = null;
      if (expr instanceof PsiReferenceExpression ref) {
        String enumConstName = evaluateEnumConstantName(ref);
        if (enumConstName != null) {
          value = enumConstName;
        }
      }
      if (value == null) {
        value = ConstantExpressionUtil.computeCastTo(expr, selectorType);
      }
      if (value == null) {
        myVisitor.report(JavaErrorKinds.SWITCH_LABEL_CONSTANT_EXPECTED.create(expr));
      }
    }
    else if (label instanceof PsiDefaultCaseLabelElement defaultElement && labelElementList.getElementCount() == 1) {
      // If default is not the only case in the label, insufficient language level will be reported
      // See JavaErrorVisitor#visitDefaultCaseLabelElement
      myVisitor.report(JavaErrorKinds.SWITCH_DEFAULT_LABEL_CONTAINS_CASE.create(defaultElement, labelElementList));
    }
    else if (label instanceof PsiPattern) {
      // Ignore patterns. If they appear here, insufficient language level will be reported
    }
  }

  private void checkLabelAndSelectorCompatibilityPattern(@NotNull PsiCaseLabelElement label, @NotNull PsiType selectorType) {
    if (label instanceof PsiDefaultCaseLabelElement) return;
    if (label instanceof PsiExpression expr) {
      Object constValue = evaluateConstant(expr);
      if (constValue == null) {
        myVisitor.report(JavaErrorKinds.SWITCH_LABEL_CONSTANT_EXPECTED.create(expr));
        return;
      }
      JavaPsiSwitchUtil.SelectorKind kind = JavaPsiSwitchUtil.getSwitchSelectorKind(selectorType);
      if (kind.getFeature() == JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS) {
        if ((kind == JavaPsiSwitchUtil.SelectorKind.LONG && !(constValue instanceof Long)) ||
            (kind == JavaPsiSwitchUtil.SelectorKind.DOUBLE && !(constValue instanceof Double)) ||
            (kind == JavaPsiSwitchUtil.SelectorKind.FLOAT && !(constValue instanceof Float)) ||
            (kind == JavaPsiSwitchUtil.SelectorKind.BOOLEAN && !(constValue instanceof Boolean))) {
          PsiType unboxedType = PsiPrimitiveType.getOptionallyUnboxedType(selectorType);
          if (unboxedType != null) {
            myVisitor.reportIncompatibleType(unboxedType, expr.getType(), expr);
          }
        }
        return;
      }
      if (ConstantExpressionUtil.computeCastTo(constValue, selectorType) == null) {
        myVisitor.reportIncompatibleType(selectorType, expr.getType(), expr);
        return;
      }
      if (kind == JavaPsiSwitchUtil.SelectorKind.INT || kind == JavaPsiSwitchUtil.SelectorKind.STRING) {
        return;
      }
      myVisitor.report(JavaErrorKinds.SWITCH_LABEL_PATTERN_EXPECTED.create(expr, selectorType));
      return;
    }
    else if (label instanceof PsiPattern) {
      PsiPattern elementToReport = JavaPsiPatternUtil.getTypedPattern(label);
      if (elementToReport == null) return;
      PsiTypeElement typeElement = JavaPsiPatternUtil.getPatternTypeElement(elementToReport);
      if (typeElement == null) return;
      PsiType patternType = typeElement.getType();
      boolean primitivesAllowed = myVisitor.isApplicable(JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS);
      if (!(patternType instanceof PsiClassType) && !(patternType instanceof PsiArrayType) && !primitivesAllowed) {
        if (patternType instanceof PsiPrimitiveType) {
          myVisitor.checkFeature(elementToReport, JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS);
          return;
        }
        myVisitor.report(JavaErrorKinds.PATTERN_EXPECTED_CLASS_OR_ARRAY_TYPE.create(elementToReport));
        return;
      }
      if ((!ContainerUtil.and(JavaPsiPatternUtil.deconstructSelectorType(selectorType),
                              type -> TypeConversionUtil.areTypesConvertible(type, patternType)) ||
           // 14.30.3 A type pattern that declares a pattern variable of a reference type U is
           // applicable at another reference type T if T is checkcast convertible to U (JEP 440-441).
           // There is no rule that says that a reference type applies to a primitive type
           (selectorType instanceof PsiPrimitiveType &&
            //from JEP 455 it is allowed
            myVisitor.isApplicable(JavaFeature.PATTERN_GUARDS_AND_RECORD_PATTERNS)) && !primitivesAllowed) &&
          //null type is applicable to any class type
          !selectorType.equals(PsiTypes.nullType())) {
        if (!IncompleteModelUtil.isIncompleteModel(label) ||
            (!IncompleteModelUtil.isPotentiallyConvertible(selectorType, patternType, label))) {
          if (selectorType instanceof PsiPrimitiveType && !primitivesAllowed) {
            myVisitor.report(JavaErrorKinds.UNSUPPORTED_FEATURE.create(elementToReport, JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS));
          }
          else {
            myVisitor.reportIncompatibleType(selectorType, patternType, elementToReport);
          }
          return;
        }
      }
      myVisitor.myPatternChecker.checkUncheckedPatternConversion(elementToReport);
      PsiDeconstructionPattern deconstructionPattern = JavaPsiPatternUtil.findDeconstructionPattern(elementToReport);
      myVisitor.myPatternChecker.checkDeconstructionErrors(deconstructionPattern);
      return;
    }
    myVisitor.report(JavaErrorKinds.SWITCH_LABEL_UNEXPECTED.create(label));
  }

  private void checkDuplicates(@NotNull PsiSwitchBlock block) {
    for (Map.Entry<Object, Collection<PsiElement>> entry : JavaPsiSwitchUtil.getValuesAndLabels(block).entrySet()) {
      if (entry.getValue().size() <= 1) continue;
      Object duplicateKey = entry.getKey();
      for (PsiElement duplicateElement : entry.getValue()) {
        myVisitor.report(JavaErrorKinds.SWITCH_LABEL_DUPLICATE.create(duplicateElement, duplicateKey));
      }
    }
  }

  private void checkFallthroughLegality(@NotNull PsiSwitchBlock block) {
    if (!myVisitor.isApplicable(JavaFeature.PATTERNS_IN_SWITCH)) return;
    PsiCodeBlock body = block.getBody();
    if (body == null) return;
    List<List<PsiSwitchLabelStatementBase>> elementsToCheckFallThroughLegality = new SmartList<>();
    int switchBlockGroupCounter = 0;
    for (PsiStatement st : body.getStatements()) {
      if (!(st instanceof PsiSwitchLabelStatementBase labelStatement)) continue;
      List<PsiSwitchLabelStatementBase> switchLabels;
      if (switchBlockGroupCounter < elementsToCheckFallThroughLegality.size()) {
        switchLabels = elementsToCheckFallThroughLegality.get(switchBlockGroupCounter);
      }
      else {
        switchLabels = new SmartList<>();
        elementsToCheckFallThroughLegality.add(switchLabels);
      }
      switchLabels.add(labelStatement);
      if (!(PsiTreeUtil.skipWhitespacesAndCommentsForward(labelStatement) instanceof PsiSwitchLabelStatement)) {
        switchBlockGroupCounter++;
      }
    }
    Set<PsiElement> alreadyFallThroughElements = new HashSet<>();
    checkFallThroughFromPatternWithSeveralLabels(elementsToCheckFallThroughLegality, alreadyFallThroughElements);
    checkFallThroughToPatternPrecedingCompleteNormally(elementsToCheckFallThroughLegality, alreadyFallThroughElements);
  }

  private void checkFallThroughFromPatternWithSeveralLabels(@NotNull List<? extends List<PsiSwitchLabelStatementBase>> switchBlockGroup,
                                                            @NotNull Set<? super PsiElement> alreadyFallThroughElements) {
    if (switchBlockGroup.isEmpty()) return;
    for (List<PsiSwitchLabelStatementBase> switchLabel : switchBlockGroup) {
      for (PsiSwitchLabelStatementBase switchLabelElement : switchLabel) {
        PsiCaseLabelElementList labelElementList = switchLabelElement.getCaseLabelElementList();
        if (labelElementList == null || labelElementList.getElementCount() == 0) continue;
        if (!checkCaseLabelCombination(labelElementList)) {
          PsiCaseLabelElement[] elements = labelElementList.getElements();
          final PsiCaseLabelElement first = elements[0];
          if (JavaPsiPatternUtil.containsNamedPatternVariable(first)) {
            PsiElement nextNotLabel = PsiTreeUtil.skipSiblingsForward(switchLabelElement, PsiWhiteSpace.class, PsiComment.class,
                                                                      PsiSwitchLabelStatement.class);
            //there is no statement, it is allowed to go through (14.11.1 JEP 440-441)
            if (!(nextNotLabel instanceof PsiStatement)) {
              continue;
            }
            if (PsiTreeUtil.skipWhitespacesAndCommentsForward(switchLabelElement) instanceof PsiSwitchLabelStatement ||
                PsiTreeUtil.skipWhitespacesAndCommentsBackward(switchLabelElement) instanceof PsiSwitchLabelStatement) {
              alreadyFallThroughElements.add(first);
              myVisitor.report(JavaErrorKinds.SWITCH_MULTIPLE_LABELS_WITH_PATTERN_VARIABLES.create(first));
            }
          }
        }
      }
    }
  }

  private boolean checkCaseLabelCombination(@NotNull PsiCaseLabelElementList labelElementList) {
    PsiCaseLabelElement[] elements = labelElementList.getElements();
    PsiCaseLabelElement firstElement = elements[0];
    if (elements.length == 1) {
      if (firstElement instanceof PsiDefaultCaseLabelElement defaultLabel) {
        myVisitor.report(JavaErrorKinds.SWITCH_DEFAULT_LABEL_CONTAINS_CASE.create(defaultLabel, labelElementList));
        return true;
      }
      return false;
    }
    if (elements.length == 2) {
      if (firstElement instanceof PsiDefaultCaseLabelElement defaultLabel &&
          elements[1] instanceof PsiExpression expr && ExpressionUtil.isNullLiteral(expr)) {
        myVisitor.report(JavaErrorKinds.SWITCH_DEFAULT_NULL_ORDER.create(defaultLabel, labelElementList));
        return true;
      }
      if (firstElement instanceof PsiExpression expr && ExpressionUtil.isNullLiteral(expr) &&
          elements[1] instanceof PsiDefaultCaseLabelElement) {
        return false;
      }
    }

    boolean unnamedAllowed = myVisitor.isApplicable(JavaFeature.UNNAMED_PATTERNS_AND_VARIABLES);
    boolean reported = false;
    for (PsiCaseLabelElement element : elements) {
      if (element instanceof PsiDefaultCaseLabelElement defaultLabel) {
        myVisitor.report(JavaErrorKinds.SWITCH_DEFAULT_LABEL_NOT_ALLOWED.create(defaultLabel));
        reported = true;
      }
      else if (element instanceof PsiExpression expr && ExpressionUtil.isNullLiteral(expr)) {
        myVisitor.report(JavaErrorKinds.SWITCH_NULL_LABEL_NOT_ALLOWED.create(expr));
        reported = true;
      }
      else if (element instanceof PsiPattern pattern && firstElement instanceof PsiExpression) {
        var kind = unnamedAllowed
                   ? JavaErrorKinds.SWITCH_LABEL_COMBINATION_CONSTANTS_AND_PATTERNS_UNNAMED
                   : JavaErrorKinds.SWITCH_LABEL_COMBINATION_CONSTANTS_AND_PATTERNS;
        myVisitor.report(kind.create(pattern));
        reported = true;
      }
    }
    if (reported) return true;

    if (firstElement instanceof PsiPattern) {
      PsiCaseLabelElement nonPattern = ContainerUtil.find(elements, e -> !(e instanceof PsiPattern));
      if (nonPattern != null) {
        var kind = unnamedAllowed
                   ? JavaErrorKinds.SWITCH_LABEL_COMBINATION_CONSTANTS_AND_PATTERNS_UNNAMED
                   : JavaErrorKinds.SWITCH_LABEL_COMBINATION_CONSTANTS_AND_PATTERNS;
        myVisitor.report(kind.create(nonPattern));
        return true;
      }
      if (!unnamedAllowed) {
        myVisitor.report(JavaErrorKinds.SWITCH_LABEL_MULTIPLE_PATTERNS.create(elements[1]));
        return true;
      }
      PsiCaseLabelElement patternVarElement = ContainerUtil.find(elements, JavaPsiPatternUtil::containsNamedPatternVariable);
      if (patternVarElement != null) {
        myVisitor.report(JavaErrorKinds.SWITCH_LABEL_MULTIPLE_PATTERNS_UNNAMED.create(patternVarElement));
        return true;
      }
    }
    return false;
  }

  private void checkFallThroughToPatternPrecedingCompleteNormally(@NotNull List<? extends List<? extends PsiSwitchLabelStatementBase>> switchBlockGroup,
                                                                  @NotNull Set<PsiElement> alreadyFallThroughElements) {
    for (int i = 1; i < switchBlockGroup.size(); i++) {
      List<? extends PsiSwitchLabelStatementBase> switchLabels = switchBlockGroup.get(i);
      PsiSwitchLabelStatementBase firstSwitchLabelInGroup = switchLabels.get(0);
      for (PsiSwitchLabelStatementBase switchLabel : switchLabels) {
        if (!(switchLabel instanceof PsiSwitchLabelStatement)) {
          return;
        }
        PsiCaseLabelElementList labelElementList = switchLabel.getCaseLabelElementList();
        if (labelElementList == null) continue;
        List<PsiCaseLabelElement> patternElements = ContainerUtil.filter(labelElementList.getElements(),
                                                                         labelElement -> JavaPsiPatternUtil.containsNamedPatternVariable(
                                                                           labelElement));
        if (patternElements.isEmpty()) continue;
        PsiStatement prevStatement = PsiTreeUtil.getPrevSiblingOfType(firstSwitchLabelInGroup, PsiStatement.class);
        if (prevStatement == null) continue;
        ControlFlow flow = ControlFlowChecker.getControlFlow(prevStatement);
        if (flow != null && ControlFlowUtil.canCompleteNormally(flow, 0, flow.getSize())) {
          List<PsiCaseLabelElement> elements =
            ContainerUtil.filter(patternElements, patternElement -> !alreadyFallThroughElements.contains(patternElement));
          for (PsiCaseLabelElement patternElement : elements) {
            myVisitor.report(JavaErrorKinds.SWITCH_FALLTHROUGH_TO_PATTERN.create(patternElement));
          }
        }
      }
    }
  }

  private void checkDominance(@NotNull PsiSwitchBlock block) {
    Map<PsiCaseLabelElement, PsiElement> dominatedLabels = JavaPsiSwitchUtil.findDominatedLabels(block);
    for (Map.Entry<PsiCaseLabelElement, PsiElement> entry : dominatedLabels.entrySet()) {
      PsiCaseLabelElement overWhom = entry.getKey();
      PsiElement who = entry.getValue();
      myVisitor.report(JavaErrorKinds.SWITCH_DOMINANCE_VIOLATION.create(overWhom, who));
    }
  }
  
  private void checkNoDefaultBranchAllowed(@NotNull PsiSwitchBlock block) {
    if (!myVisitor.isApplicable(JavaFeature.PATTERNS_IN_SWITCH)) return;
    //T is an intersection type T1& ... &Tn, and P covers Ti, for one of the type Ti (1≤i≤n)
    PsiCaseLabelElement elementCoversType = JavaPsiSwitchUtil.getUnconditionalPatternLabel(block);
    PsiElement defaultElement = JavaPsiSwitchUtil.findDefaultElement(block);
    if (defaultElement != null && elementCoversType != null) {
      myVisitor.report(JavaErrorKinds.SWITCH_UNCONDITIONAL_PATTERN_AND_DEFAULT.create(defaultElement.getFirstChild()));
      myVisitor.report(JavaErrorKinds.SWITCH_UNCONDITIONAL_PATTERN_AND_DEFAULT.create(elementCoversType));
      return;
    }
    //default (or unconditional), TRUE and FALSE cannot be together
    if ((defaultElement != null || elementCoversType != null) &&
        JavaPsiSwitchUtil.isBooleanSwitchWithTrueAndFalse(block)) {
      if (defaultElement != null) {
        myVisitor.report(JavaErrorKinds.SWITCH_DEFAULT_AND_BOOLEAN.create(defaultElement.getFirstChild()));
      }
      if (elementCoversType != null) {
        myVisitor.report(JavaErrorKinds.SWITCH_UNCONDITIONAL_PATTERN_AND_BOOLEAN.create(elementCoversType));
      }
    }
  }

  void checkExhaustiveness(@NotNull PsiSwitchBlock block) {
    PsiCodeBlock body = block.getBody();
    if (body == null) return;

    if (!ExpressionUtil.isEnhancedSwitch(block)) return;
    if (JavaPsiSwitchUtil.getUnconditionalPatternLabel(block) != null) return;
    if (JavaPsiSwitchUtil.findDefaultElement(block) != null) return;
    if (!hasExhaustivenessError(block)) return;

    boolean hasAnyCaseLabels = JavaPsiSwitchUtil.hasAnyCaseLabels(block);
    var kind = hasAnyCaseLabels ? JavaErrorKinds.SWITCH_INCOMPLETE : JavaErrorKinds.SWITCH_EMPTY;
    myVisitor.report(kind.create(block));
  }
}
