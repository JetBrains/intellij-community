// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.JavaPsiPatternUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.intellij.codeInspection.options.OptPane.checkbox;

public final class PatternVariablesCanBeReplacedWithCastInspection extends AbstractBaseJavaLocalInspectionTool {

  @SuppressWarnings("PublicField")
  public boolean tryToPreserveUnusedVariables = true;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return OptPane.pane(checkbox("tryToPreserveUnusedVariables",
                                 JavaBundle.message("inspection.message.pattern.variables.can.be.replaced.with.cast.preserve.option")));
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitInstanceOfExpression(@NotNull PsiInstanceOfExpression expression) {
        PsiPrimaryPattern pattern = expression.getPattern();
        if (pattern == null) {
          return;
        }

        //without support deconstruction
        if (assignedVariablesInCondition(expression) ||
            !(pattern instanceof PsiTypeTestPattern typeTestPattern) ||
            typeTestPattern.getPatternVariable() == null) {
          return;
        }
        LocalQuickFix quickFix = new ConvertInstanceOfPatternToCastFix(pattern, tryToPreserveUnusedVariables);

        if (InspectionProjectProfileManager.isInformationLevel(getShortName(), pattern)) {
          holder.registerProblem(expression, TextRange.create(expression.getOperand().getStartOffsetInParent(),
                                                              pattern.getStartOffsetInParent() + pattern.getTextLength()),
                                 JavaBundle.message("inspection.message.pattern.variables.can.be.replaced.with.cast"), quickFix);
        }
        else {
          holder.registerProblem(expression, TextRange.create(pattern.getStartOffsetInParent(),
                                                              pattern.getStartOffsetInParent() + pattern.getTextLength()),
                                 JavaBundle.message("inspection.message.pattern.variables.can.be.replaced.with.cast"), quickFix);
        }
      }

      private static boolean assignedVariablesInCondition(PsiInstanceOfExpression psiInstanceOfExpression) {
        List<PsiPatternVariable> variables = JavaPsiPatternUtil.getExposedPatternVariables(psiInstanceOfExpression);

        PsiElement upperLevel = getUpperLevelOfCondition(psiInstanceOfExpression);
        for (PsiPatternVariable variable : variables) {
          if (VariableAccessUtils.variableIsAssigned(variable, upperLevel)) {
            return true;
          }
        }

        PsiConditionalLoopStatement conditionalLoopStatement =
          PsiTreeUtil.getParentOfType(psiInstanceOfExpression, PsiConditionalLoopStatement.class);
        if (conditionalLoopStatement instanceof PsiForStatement forStatement) {
          PsiStatement update = forStatement.getUpdate();
          for (PsiPatternVariable variable : variables) {
            if (VariableAccessUtils.variableIsAssigned(variable, update)) {
              return true;
            }
          }
        }
        return false;
      }

      private static PsiElement getUpperLevelOfCondition(PsiExpression expression) {
        PsiElement current = expression;
        PsiElement parent = current.getParent();
        while (parent instanceof PsiParenthesizedExpression ||
               parent instanceof PsiPolyadicExpression ||
               parent instanceof PsiPrefixExpression ||
               parent instanceof PsiConditionalExpression) {
          current = parent;
          parent = current.getParent();
        }
        return current;
      }
    };
  }

  private enum ConditionState {
    TRUE, FALSE, UNKNOWN
  }

  private static class ConvertInstanceOfPatternToCastFix extends PsiUpdateModCommandQuickFix {

    private final String myName;
    private final boolean tryToPreserveUnusedVariables;

    private ConvertInstanceOfPatternToCastFix(@NotNull PsiElement psiElement, boolean tryToPreserveUnusedVariables) {
      myName = psiElement.getText();
      this.tryToPreserveUnusedVariables = tryToPreserveUnusedVariables;
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getName() {
      return JavaBundle.message("inspection.message.pattern.variables.can.be.replaced.with.cast.fix.name", myName);
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return JavaBundle.message("inspection.message.pattern.variables.can.be.replaced.with.cast.family.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (!(element instanceof PsiInstanceOfExpression expression)) {
        return;
      }
      PsiPrimaryPattern pattern = expression.getPattern();
      if (!(pattern instanceof PsiTypeTestPattern typeTestPattern)) {
        return;
      }
      PsiPatternVariable variable = typeTestPattern.getPatternVariable();
      if (variable == null) {
        return;
      }

      List<PsiReferenceExpression> references = VariableAccessUtils.getVariableReferences(variable, variable.getDeclarationScope());
      processReferences(references, variable, expression);
      deletePatternFromInstanceOf(expression);
    }

    private void processReferences(@NotNull List<PsiReferenceExpression> references,
                                   @NotNull PsiPatternVariable variable,
                                   @NotNull PsiInstanceOfExpression psiInstanceOfExpression) {
      PsiIfStatement ifStatement = PsiTreeUtil.getParentOfType(psiInstanceOfExpression, PsiIfStatement.class);
      if (ifStatement != null && PsiTreeUtil.isAncestor(ifStatement.getCondition(), psiInstanceOfExpression, false)) {
        processReferencesForIfStatement(ifStatement, variable, psiInstanceOfExpression, references);
        return;
      }

      PsiConditionalLoopStatement conditionalLoopStatement =
        PsiTreeUtil.getParentOfType(psiInstanceOfExpression, PsiConditionalLoopStatement.class);
      if (conditionalLoopStatement != null &&
          PsiTreeUtil.isAncestor(conditionalLoopStatement.getCondition(), psiInstanceOfExpression, false)) {
        processReferencesForLoopStatement(conditionalLoopStatement, variable, psiInstanceOfExpression, references);
        return;
      }

      replaceWithCast(variable, references);
    }

    private void processReferencesForIfStatement(@NotNull PsiIfStatement ifStatement,
                                                 @NotNull PsiPatternVariable variable,
                                                 @NotNull PsiInstanceOfExpression psiInstanceOfExpression,
                                                 @NotNull List<PsiReferenceExpression> references) {
      List<PsiReferenceExpression> unusedReferences = new ArrayList<>(references);

      PsiStatement thenBranch = ifStatement.getThenBranch();
      if (this.tryToPreserveUnusedVariables) {
        //trivial cases
        ConditionState conditionState = getConditionIfInstanceOfTrue(psiInstanceOfExpression, ifStatement.getCondition());

        if (conditionState == ConditionState.TRUE &&
            thenBranch != null && !(thenBranch instanceof PsiEmptyStatement) &&
            ifStatement.getElseBranch() == null) {
          List<PsiReferenceExpression> referencesForThenBranch =
            ContainerUtil.filter(unusedReferences, t -> PsiTreeUtil.isAncestor(thenBranch, t, false));
          unusedReferences.removeAll(referencesForThenBranch);
          addDeclarationInsideBlock(thenBranch, variable);
        }

        if (conditionState.equals(ConditionState.FALSE) && !ControlFlowUtils.statementMayCompleteNormally(thenBranch) &&
            ifStatement.getElseBranch() == null &&
            !(ifStatement.getParent() instanceof PsiIfStatement parentIf && parentIf.getElseBranch() == ifStatement)) {

          List<PsiReferenceExpression> referencesForOutsideIf =
            ContainerUtil.filter(unusedReferences, t -> !PsiTreeUtil.isAncestor(ifStatement, t, false));
          unusedReferences.removeAll(referencesForOutsideIf);
          addDeclarationOutsideBlock(ifStatement, variable);
        }
      }

      //insert statements and casts in place
      Map<Boolean, List<PsiReferenceExpression>> collectedInsideThenBranch = unusedReferences.stream()
        .collect(
          Collectors.groupingBy(referenceExpression -> PsiTreeUtil.isAncestor(thenBranch, referenceExpression, false)));
      if (thenBranch != null && !(thenBranch instanceof PsiEmptyStatement) && collectedInsideThenBranch.get(Boolean.TRUE) != null) {
        addDeclarationInsideBlock(thenBranch, variable);
      }


      //in else branch
      PsiStatement elseBranch = ifStatement.getElseBranch();
      Map<Boolean, List<PsiReferenceExpression>> collectedInsideElseBlock =
        collectedInsideThenBranch.getOrDefault(Boolean.FALSE, List.of()).stream()
          .collect(Collectors.groupingBy(referenceExpression -> PsiTreeUtil.isAncestor(elseBranch, referenceExpression, false)));

      if (elseBranch != null && collectedInsideElseBlock.get(Boolean.TRUE) != null) {
        if (elseBranch instanceof PsiIfStatement elseIfStatement) {
          processReferencesForIfStatement(elseIfStatement, variable, psiInstanceOfExpression, collectedInsideElseBlock.get(Boolean.TRUE));
        }
        else {
          addDeclarationInsideBlock(elseBranch, variable);
        }
      }

      //outside
      Map<Boolean, List<PsiReferenceExpression>> collectedOutside = collectedInsideElseBlock.getOrDefault(Boolean.FALSE, List.of()).stream()
        .collect(Collectors.groupingBy(referenceExpression -> !PsiTreeUtil.isAncestor(ifStatement, referenceExpression, false)));

      if (collectedOutside.get(Boolean.TRUE) != null) {
        addDeclarationOutsideBlock(ifStatement, variable);
      }

      //typeCast - for example, in Conditions
      replaceWithCast(variable, collectedOutside.get(Boolean.FALSE));
    }

    private void processReferencesForLoopStatement(@NotNull PsiConditionalLoopStatement statement,
                                                   @NotNull PsiPatternVariable variable,
                                                   @NotNull PsiInstanceOfExpression psiInstanceOfExpression,
                                                   @NotNull List<PsiReferenceExpression> references) {

      //trivial cases
      ConditionState conditionState = getConditionIfInstanceOfTrue(psiInstanceOfExpression, statement.getCondition());
      List<PsiReferenceExpression> unusedReferences = new ArrayList<>(references);
      List<PsiReferenceExpression> referencesForInsideBlock =
        ContainerUtil.filter(unusedReferences, t -> PsiTreeUtil.isAncestor(statement.getBody(), t, false));

      unusedReferences.removeAll(referencesForInsideBlock);
      if (statement.getBody() != null && !(statement.getBody() instanceof PsiEmptyStatement) &&
          ((this.tryToPreserveUnusedVariables && conditionState == ConditionState.TRUE && !(statement instanceof PsiDoWhileStatement)) ||
           !referencesForInsideBlock.isEmpty())) {
        addDeclarationInsideBlock(statement.getBody(), variable);
      }

      List<PsiReferenceExpression> referencesOutsideLoop =
        ContainerUtil.filter(unusedReferences, t -> !PsiTreeUtil.isAncestor(statement, t, false));
      unusedReferences.removeAll(referencesOutsideLoop);

      boolean noBreak = PsiTreeUtil.processElements(statement,
                                                    e -> !(e instanceof PsiBreakStatement) ||
                                                         ((PsiBreakStatement)e).findExitedStatement() != statement);

      if ((this.tryToPreserveUnusedVariables && conditionState == ConditionState.FALSE && noBreak) ||
          !referencesOutsideLoop.isEmpty()) {
        addDeclarationOutsideBlock(statement, variable);
      }

      //other cases
      replaceWithCast(variable, unusedReferences);
    }

    private static ConditionState getConditionIfInstanceOfTrue(PsiInstanceOfExpression expression, PsiExpression condition) {
      if (!(condition != null && PsiTreeUtil.skipParentsOfType(expression, PsiParenthesizedExpression.class, PsiPrefixExpression.class,
                                                               PsiPolyadicExpression.class) == condition.getParent())) {
        return ConditionState.UNKNOWN;
      }
      PsiElement current = expression.getParent();
      boolean currentState = true;
      while (current != condition.getParent()) {
        if (current instanceof PsiParenthesizedExpression) {
          current = current.getParent();
          continue;
        }
        if (current instanceof PsiPrefixExpression prefixExpression) {
          if (!prefixExpression.getOperationTokenType().equals(JavaTokenType.EXCL)) {
            return ConditionState.UNKNOWN;
          }
          currentState = !currentState;
          current = current.getParent();
          continue;
        }
        if (current instanceof PsiPolyadicExpression polyadicExpression) {
          IElementType tokenType = polyadicExpression.getOperationTokenType();
          if (tokenType.equals(JavaTokenType.ANDAND) && currentState) {
            current = current.getParent();
            continue;
          }
          if (tokenType.equals(JavaTokenType.OROR) && !currentState) {
            current = current.getParent();
            continue;
          }
          return ConditionState.UNKNOWN;
        }
      }
      return currentState ? ConditionState.TRUE : ConditionState.FALSE;
    }


    private static void addDeclarationOutsideBlock(PsiStatement statement, PsiPatternVariable variable) {

      if (statement.getNextSibling() == null) {
        return;
      }
      String text = getDeclarationStatement(variable);
      if (text == null) {
        return;
      }

      PsiElement parent = statement.getParent();
      if (parent == null) {
        return;
      }

      Project project = statement.getProject();
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      PsiStatement declarationStatement = factory.createStatementFromText(text, statement.getNextSibling());

      PsiElement newDeclarationStatement = parent.addAfter(declarationStatement, statement);
      CodeStyleManager.getInstance(project).reformat(newDeclarationStatement);
    }

    private static void addDeclarationInsideBlock(@NotNull PsiStatement statement, @NotNull PsiPatternVariable variable) {
      String text = getDeclarationStatement(variable);
      if (text == null) {
        return;
      }
      Project project = statement.getProject();
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      PsiStatement declarationStatement = factory.createStatementFromText(text, statement);
      BlockUtils.addBefore(statement, declarationStatement);
    }

    @Nullable
    private static String getDeclarationStatement(@NotNull PsiPatternVariable variable) {
      String text = JavaPsiPatternUtil.getEffectiveInitializerText(variable);
      if (text == null) {
        return null;
      }
      text = variable.getTypeElement().getText() + " " + variable.getName() + " = " + text + ";";
      PsiModifierList modifierList = variable.getModifierList();
      if (modifierList != null && StringUtil.isNotEmpty(modifierList.getText())) {
        text = modifierList.getText() + " " + text;
      }
      return text;
    }

    private static void deletePatternFromInstanceOf(@NotNull PsiInstanceOfExpression expression) {
      if (!(expression.getPattern() instanceof PsiTypeTestPattern typeTestPattern)) {
        return;
      }
      PsiPatternVariable variable = typeTestPattern.getPatternVariable();
      if (variable == null) {
        return;
      }
      String text = expression.getText();
      text = text.substring(0, text.length() - typeTestPattern.getTextLength()) + variable.getTypeElement().getText();

      CommentTracker tracker = new CommentTracker();
      for (PsiElement element : expression.getChildren()) {
        if (element == typeTestPattern) {
          continue;
        }
        tracker.markUnchanged(element);
      }
      PsiReplacementUtil.replaceExpression(expression, text, tracker);
    }

    private static void replaceWithCast(@NotNull PsiPatternVariable variable, @Nullable List<PsiReferenceExpression> references) {
      if (references == null || references.isEmpty()) {
        return;
      }

      String text = JavaPsiPatternUtil.getEffectiveInitializerText(variable);
      if (text == null) {
        return;
      }

      references.forEach(reference -> {
        PsiReplacementUtil.replaceExpression(reference, text, new CommentTracker());
      });
    }
  }
}