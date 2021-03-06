// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.codeInspection.util.LambdaGenerationUtil;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.JavaFeature;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
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
    return new SingleCheckboxOptionsPanel(JavaBundle.message("inspection.explicit.array.filling.suggest.set.all"),
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
          PsiType lType = container.getElementType();
          Object defaultValue = PsiTypesUtil.getDefaultValue(lType);
          if (isDefaultValue(rValue, defaultValue, lType) && isFilledWithDefaultValues(container.getQualifier(), statement, defaultValue)) {
            holder.registerProblem(statement, getRange(statement, ProblemHighlightType.WARNING),
                                   JavaBundle.message("inspection.explicit.array.filling.redundant.loop.description"),
                                   QuickFixFactory.getInstance()
                                     .createDeleteFix(statement, CommonQuickFixBundle.message("fix.remove.statement", PsiKeyword.FOR)));
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

      private boolean isDefaultValue(@NotNull PsiExpression expression, @Nullable Object defaultValue, @Nullable PsiType lType) {
        if (ExpressionUtils.isNullLiteral(expression) && defaultValue == null) return true;
        Object constantValue = ExpressionUtils.computeConstantExpression(expression);
        PsiType rType = expression.getType();
        if (rType instanceof PsiPrimitiveType && lType instanceof PsiPrimitiveType) {
          if (defaultValue instanceof Number && constantValue instanceof Number) {
            return ((Number)defaultValue).doubleValue() == ((Number)constantValue).doubleValue();
          }
        }
        return constantValue != null && constantValue.equals(defaultValue);
      }

      private boolean isFilledWithDefaultValues(@NotNull PsiExpression expression,
                                                @NotNull PsiForStatement statement,
                                                @Nullable Object defaultValue) {
        PsiReferenceExpression arrayRef = tryCast(PsiUtil.skipParenthesizedExprDown(expression), PsiReferenceExpression.class);
        if (arrayRef == null) return false;
        PsiVariable arrayVar = tryCast(arrayRef.resolve(), PsiVariable.class);
        if (arrayVar == null) return false;
        PsiCodeBlock block = tryCast(PsiUtil.getVariableCodeBlock(arrayVar, null), PsiCodeBlock.class);
        if (block == null) return false;
        ControlFlow flow = createControlFlow(block);
        if (flow == null) return false;
        int statementStart = flow.getStartOffset(statement);
        if (statementStart == -1) return false;
        int statementEnd = flow.getEndOffset(statement);
        if (statementEnd == -1) return false;
        PsiElement[] defs = getDefs(block, arrayVar, arrayRef, defaultValue);
        if (defs == null) return false;
        Set<Integer> exclude = getDefsOffsets(flow, defs);
        if (exclude == null) return false;
        for (int i = statementStart; i < statementEnd; i++) {
          exclude.add(i);
        }
        return Arrays.stream(defs)
          .map(def -> flow.getEndOffset(def))
          .noneMatch(offset -> ControlFlowUtils.isVariableReferencedBeforeStatementEntry(flow, offset + 1, statement, arrayVar, exclude));
      }

      @Nullable
      private ControlFlow createControlFlow(@NotNull PsiCodeBlock block) {
        try {
          return ControlFlowFactory.getInstance(block.getProject())
            .getControlFlow(block, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance());
        }
        catch (AnalysisCanceledException ignored) {
          return null;
        }
      }

      private PsiElement @Nullable [] getDefs(@NotNull PsiCodeBlock block,
                                              @NotNull PsiVariable arrayVar,
                                              @NotNull PsiReferenceExpression arrayRef,
                                              @Nullable Object defaultValue) {
        PsiElement[] defs = DefUseUtil.getDefs(block, arrayVar, arrayRef);
        PsiExpression[] expressions = new PsiExpression[defs.length];
        for (int i = 0; i < defs.length; i++) {
          PsiElement def = defs[i];
          PsiVariable variable = tryCast(def, PsiVariable.class);
          if (variable != null) {
            PsiExpression initializer = variable.getInitializer();
            if (!isNewArrayCreation(initializer, defaultValue)) return null;
            expressions[i] = initializer;
            continue;
          }
          PsiAssignmentExpression assignment = PsiTreeUtil.getParentOfType(def, PsiAssignmentExpression.class);
          if (assignment != null) {
            if (!isNewArrayCreation(assignment.getRExpression(), defaultValue)) return null;
            expressions[i] = assignment;
            continue;
          }
          return null;
        }
        return expressions;
      }

      private boolean isNewArrayCreation(@Nullable PsiExpression expression, @Nullable Object defaultValue) {
        PsiExpression arrInitExpr = PsiUtil.skipParenthesizedExprDown(expression);
        PsiNewExpression newExpression = tryCast(arrInitExpr, PsiNewExpression.class);
        PsiArrayInitializerExpression initializer =
          newExpression == null ? tryCast(arrInitExpr, PsiArrayInitializerExpression.class) : newExpression.getArrayInitializer();
        if (initializer == null) return true;
        return Arrays.stream(initializer.getInitializers()).allMatch(init -> isDefaultValue(init, defaultValue, init.getType()));
      }

      @Nullable
      private Set<Integer> getDefsOffsets(@NotNull ControlFlow flow, PsiElement @NotNull [] defs) {
        Set<Integer> set = new HashSet<>();
        for (PsiElement def : defs) {
          int start = flow.getStartOffset(def);
          if (start == -1) return null;
          int end = flow.getEndOffset(def);
          if (end == -1) return null;
          for (int i = start; i < end; i++) {
            set.add(i);
          }
        }
        return set;
      }

      private void registerProblem(@NotNull PsiForStatement statement, boolean isSetAll) {
        String message = JavaBundle.message("inspection.explicit.array.filling.description", isSetAll ? "setAll" : "fill");
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
                                       JavaBundle.message("inspection.explicit.array.filling.no.suggestion.for.set.all"),
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

  private static final class ReplaceWithArraysCallFix implements LocalQuickFix {

    private final boolean myIsRhsConstant;

    private ReplaceWithArraysCallFix(boolean isRhsConstant) {
      myIsRhsConstant = isRhsConstant;
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return JavaBundle.message("inspection.explicit.array.filling.fix.family.name", myIsRhsConstant ? "fill" : "setAll");
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
        String cast = getCast(statement, container.getElementType(), rValue.getType());
        String replacement = CommonClassNames.JAVA_UTIL_ARRAYS + ".fill(" +
                             ct.text(container.getQualifier()) + ", " + cast + ct.text(rValue) + ");";
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

    @NotNull
    private static String getCast(@NotNull PsiElement context, @Nullable PsiType elementType, @Nullable PsiType rType) {
      if (elementType == null || rType == null) return "";
      PsiType assignTo = tryCast(elementType, PsiPrimitiveType.class);
      if (assignTo == null) assignTo = TypeUtils.getObjectType(context);
      return TypeConversionUtil.isAssignable(assignTo, rType) ? "" : "(" + elementType.getCanonicalText() + ")";
    }
  }
}
