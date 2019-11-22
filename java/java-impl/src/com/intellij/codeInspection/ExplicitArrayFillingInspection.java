// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.codeInspection.util.LambdaGenerationUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.JavaFeature;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.controlFlow.DefUseUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

import static com.intellij.util.ObjectUtils.tryCast;

public class ExplicitArrayFillingInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance(ExplicitArrayFillingInspection.class);

  public boolean mySuggestSetAll = false;

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionsBundle.message("inspection.explicit.array.filling.suggest.set.all"),
                                          this,
                                          "mySuggestSetAll");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitForStatement(PsiForStatement statement) {
        super.visitForStatement(statement);
        CountingLoop loop = CountingLoop.from(statement);
        if (loop == null || loop.isIncluding() || loop.isDescending()) return;
        if (!ExpressionUtils.isZero(loop.getInitializer())) return;
        IndexedContainer container = IndexedContainer.fromLengthExpression(loop.getBound());
        if (container == null || !(container.getQualifier().getType() instanceof PsiArrayType)) return;
        PsiAssignmentExpression assignment = ExpressionUtils.getAssignment(ControlFlowUtils.stripBraces(statement.getBody()));
        if (assignment == null) return;
        PsiExpression index = container.extractIndexFromGetExpression(assignment.getLExpression());
        if (!ExpressionUtils.isReferenceTo(index, loop.getCounter())) return;
        PsiExpression rValue = assignment.getRExpression();
        if (rValue == null) return;
        if (!isChangedInLoop(loop, rValue)) {
          if (!ControlFlowUtils.isInLoop(statement) &&
              isDefaultValueAssigned(assignment, rValue) &&
              isFilledWithDefaultValues(container.getQualifier(), statement)) {
            holder.registerProblem(statement, getRange(statement, ProblemHighlightType.WARNING),
                                   InspectionsBundle.message("inspection.explicit.array.filling.redundant.loop.description"),
                                   QuickFixFactory.getInstance().createDeleteFix(statement));
            return;
          }
          registerProblem(statement, false);
          return;
        }
        if (!JavaFeature.ADVANCED_COLLECTIONS_API.isFeatureSupported(holder.getFile())) return;
        if (!StreamApiUtil.isSupportedStreamElement(container.getElementType())) return;
        if (!LambdaGenerationUtil.canBeUncheckedLambda(rValue, Predicate.isEqual(loop.getCounter()))) return;
        registerProblem(statement, true);
      }

      private boolean isChangedInLoop(@NotNull CountingLoop loop, @NotNull PsiExpression rValue) {
        if (VariableAccessUtils.collectUsedVariables(rValue).contains(loop.getCounter()) ||
            SideEffectChecker.mayHaveSideEffects(rValue)) {
          return true;
        }
        return ExpressionUtils.nonStructuralChildren(rValue)
          .filter(c -> c instanceof PsiCallExpression)
          .anyMatch(call -> !ClassUtils.isImmutable(call.getType()) && !ConstructionUtils.isEmptyArrayInitializer(call));
      }

      private boolean isDefaultValueAssigned(@NotNull PsiAssignmentExpression assignment, @NotNull PsiExpression rhs) {
        Object defaultValue = PsiTypesUtil.getDefaultValue(assignment.getType());
        if (ExpressionUtils.isNullLiteral(rhs) && defaultValue == null) return true;
        Object constantValue = ExpressionUtils.computeConstantExpression(rhs);
        return constantValue != null && constantValue.equals(defaultValue);
      }

      private boolean isFilledWithDefaultValues(@NotNull PsiExpression expression, @NotNull PsiForStatement forStatement) {
        PsiReferenceExpression arrayRef = tryCast(PsiUtil.skipParenthesizedExprDown(expression), PsiReferenceExpression.class);
        if (arrayRef == null) return false;
        PsiVariable arrayVar = tryCast(arrayRef.resolve(), PsiVariable.class);
        if (arrayVar == null) return false;
        PsiCodeBlock block = tryCast(PsiUtil.getVariableCodeBlock(arrayVar, null), PsiCodeBlock.class);
        if (block == null) return false;
        Set<PsiStatement> defs = getDefsStatements(DefUseUtil.getDefs(block, arrayVar, arrayRef));
        if (defs == null) return false;
        return !isUsedBetween(forStatement, defs, arrayVar, block);
      }

      private boolean isUsedBetween(@NotNull PsiElement ref, @NotNull Set<PsiStatement> defs,
                                    @NotNull PsiVariable arrayVar, @NotNull PsiCodeBlock block) {
        Ref<Boolean> isUsed = Ref.create(false);

        block.accept(new JavaRecursiveElementWalkingVisitor() {

          boolean inContext;

          @Override
          public void visitStatement(PsiStatement statement) {
            if (defs.contains(statement)) {
              inContext = true;
            }
            else {
              if (statement == ref) {
                inContext = false;
                stopWalking();
              }
              super.visitStatement(statement);
            }
          }

          @Override
          public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
            if (inContext && reference.isReferenceTo(arrayVar)) {
              isUsed.set(true);
              stopWalking();
            }
            super.visitReferenceElement(reference);
          }
        });
        return isUsed.get();
      }

      @Nullable
      private Set<PsiStatement> getDefsStatements(@NotNull PsiElement[] defs) {
        Set<PsiStatement> statements = new HashSet<>();
        for (PsiElement def : defs) {
          PsiVariable variable = tryCast(def, PsiVariable.class);
          if (variable != null) {
            PsiExpression initializer = variable.getInitializer();
            if (initializer == null || !isNewArrayCreation(initializer)) return null;
            PsiDeclarationStatement declaration = PsiTreeUtil.getParentOfType(initializer, PsiDeclarationStatement.class);
            if (declaration == null) return null;
            statements.add(declaration);
            continue;
          }
          PsiAssignmentExpression assignment = PsiTreeUtil.getParentOfType(def, PsiAssignmentExpression.class);
          if (assignment != null) {
            if (!isNewArrayCreation(assignment.getRExpression())) return null;
            PsiExpressionStatement expressionStatement = PsiTreeUtil.getParentOfType(assignment, PsiExpressionStatement.class);
            if (expressionStatement == null) return null;
            statements.add(expressionStatement);
            continue;
          }
          return null;
        }
        return statements;
      }

      private boolean isNewArrayCreation(@Nullable PsiExpression expression) {
        expression = PsiUtil.skipParenthesizedExprDown(expression);
        return expression == null || expression instanceof PsiNewExpression;
      }

      private void registerProblem(@NotNull PsiForStatement statement, boolean isSetAll) {
        String message = InspectionsBundle.message("inspection.explicit.array.filling.description", isSetAll ? "setAll" : "fill");
        ReplaceWithArraysCallFix fix = new ReplaceWithArraysCallFix(!isSetAll);
        ProblemHighlightType type = ProblemHighlightType.WARNING;
        if (isSetAll && !mySuggestSetAll) {
          if (!isOnTheFly) return;
          type = ProblemHighlightType.INFORMATION;
        }
        TextRange range = getRange(statement, type);

        if (isSetAll && mySuggestSetAll && isOnTheFly) {
          SetInspectionOptionFix disableForSetAllFix =
            new SetInspectionOptionFix(ExplicitArrayFillingInspection.this,
                                       "mySuggestSetAll",
                                       InspectionsBundle.message("inspection.explicit.array.filling.no.suggestion.for.set.all"),
                                       false);
          holder.registerProblem(statement, message, type, range, fix, disableForSetAllFix);
          return;
        }

        holder.registerProblem(statement, message, type, range, fix);
      }

      @NotNull
      private TextRange getRange(@NotNull PsiForStatement statement, @NotNull ProblemHighlightType type) {
        PsiStatement initialization = statement.getInitialization();
        LOG.assertTrue(initialization != null);
        TextRange range = TextRange.from(initialization.getStartOffsetInParent(), initialization.getTextLength());
        boolean wholeStatement = isOnTheFly &&
                                 (InspectionProjectProfileManager.isInformationLevel(getShortName(), statement) ||
                                  type == ProblemHighlightType.INFORMATION);
        PsiJavaToken rParenth = statement.getRParenth();
        if (wholeStatement && rParenth != null) {
          range = new TextRange(0, rParenth.getStartOffsetInParent() + 1);
        }
        return range;
      }
    };
  }

  private static class ReplaceWithArraysCallFix implements LocalQuickFix {

    private final boolean myIsRhsConstant;

    private ReplaceWithArraysCallFix(boolean isRhsConstant) {
      myIsRhsConstant = isRhsConstant;
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionsBundle.message("inspection.explicit.array.filling.fix.family.name", myIsRhsConstant ? "fill" : "setAll");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiForStatement statement = tryCast(descriptor.getStartElement(), PsiForStatement.class);
      if (statement == null) return;
      CountingLoop loop = CountingLoop.from(statement);
      if (loop == null) return;
      IndexedContainer container = IndexedContainer.fromLengthExpression(loop.getBound());
      if (container == null) return;
      PsiAssignmentExpression assignment = ExpressionUtils.getAssignment(ControlFlowUtils.stripBraces(statement.getBody()));
      if (assignment == null) return;
      PsiExpression rValue = assignment.getRExpression();
      if (rValue == null) return;
      CommentTracker ct = new CommentTracker();
      PsiElement result;
      if (myIsRhsConstant) {
        String replacement = CommonClassNames.JAVA_UTIL_ARRAYS + ".fill(" +
                             ct.text(container.getQualifier()) + ", " + ct.text(rValue) + ");";
        result = ct.replaceAndRestoreComments(statement, replacement);
      }
      else {
        String replacement = CommonClassNames.JAVA_UTIL_ARRAYS + ".setAll(" +
                             ct.text(container.getQualifier()) + ", " + loop.getCounter().getName() + "->" + ct.text(rValue) + ");";
        result = ct.replaceAndRestoreComments(statement, replacement);
        LambdaCanBeMethodReferenceInspection.replaceAllLambdasWithMethodReferences(result);
      }
      result = JavaCodeStyleManager.getInstance(project).shortenClassReferences(result);
      CodeStyleManager.getInstance(project).reformat(result);
    }
  }
}
