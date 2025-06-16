// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.util.LambdaGenerationUtil;
import com.intellij.java.JavaBundle;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
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
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;
import static com.intellij.util.ObjectUtils.tryCast;

public final class ExplicitArrayFillingInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance(ExplicitArrayFillingInspection.class);

  public boolean mySuggestSetAll = false;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("mySuggestSetAll", JavaBundle.message("inspection.explicit.array.filling.suggest.set.all")));
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitForStatement(@NotNull PsiForStatement statement) {
        super.visitForStatement(statement);
        CountingLoop loop = CountingLoop.from(statement);
        if (loop == null || loop.isIncluding() || loop.isDescending()) return;
        if (!ExpressionUtils.isZero(loop.getInitializer())) return;
        PsiAssignmentExpression assignment = ExpressionUtils.getAssignment(ControlFlowUtils.stripBraces(statement.getBody()));
        if (assignment == null) return;
        IndexedContainer container = getContainer(loop, assignment);
        if (container == null || !(container.getQualifier().getType() instanceof PsiArrayType)) return;
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
                                     .createDeleteFix(statement, CommonQuickFixBundle.message("fix.remove.statement", JavaKeywords.FOR)));
            return;
          }
          registerProblem(statement, false);
          return;
        }
        if (!PsiUtil.isAvailable(JavaFeature.ADVANCED_COLLECTIONS_API, holder.getFile())) return;
        if (!StreamApiUtil.isSupportedStreamElement(container.getElementType())) return;
        if (!LambdaGenerationUtil.canBeUncheckedLambda(rValue, Predicate.isEqual(loop.getCounter()))) return;
        registerProblem(statement, true);
      }

      private static boolean isChangedInLoop(@NotNull CountingLoop loop, @NotNull PsiExpression rValue) {
        if (VariableAccessUtils.collectUsedVariables(rValue).contains(loop.getCounter()) ||
            SideEffectChecker.mayHaveSideEffects(rValue)) {
          return true;
        }
        return ExpressionUtils.nonStructuralChildren(rValue)
          .filter(c -> c instanceof PsiCallExpression)
          .anyMatch(call -> !ClassUtils.isImmutable(call.getType()) && !ConstructionUtils.isEmptyArrayInitializer(call));
      }

      private static boolean isDefaultValue(@NotNull PsiExpression expression, @Nullable Object defaultValue, @Nullable PsiType lType) {
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

      private static boolean isFilledWithDefaultValues(@NotNull PsiExpression expression,
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
        return !ContainerUtil.exists(defs, def ->
          ControlFlowUtils.isVariableReferencedBeforeStatementEntry(flow, flow.getEndOffset(def) + 1, statement, arrayVar, exclude));
      }

      private static @Nullable ControlFlow createControlFlow(@NotNull PsiCodeBlock block) {
        try {
          return ControlFlowFactory.getInstance(block.getProject())
            .getControlFlow(block, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance());
        }
        catch (AnalysisCanceledException ignored) {
          return null;
        }
      }

      private static PsiElement @Nullable [] getDefs(@NotNull PsiCodeBlock block,
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

      private static boolean isNewArrayCreation(@Nullable PsiExpression expression, @Nullable Object defaultValue) {
        PsiExpression arrInitExpr = PsiUtil.skipParenthesizedExprDown(expression);
        PsiNewExpression newExpression = tryCast(arrInitExpr, PsiNewExpression.class);
        PsiArrayInitializerExpression initializer;
        if (newExpression == null) {
          initializer = tryCast(arrInitExpr, PsiArrayInitializerExpression.class);
          if (initializer == null) return false;
        }
        else {
          initializer = newExpression.getArrayInitializer();
        }
        if (initializer == null) return true;
        return ContainerUtil.and(initializer.getInitializers(), init -> isDefaultValue(init, defaultValue, init.getType()));
      }

      private static @Nullable Set<Integer> getDefsOffsets(@NotNull ControlFlow flow, PsiElement @NotNull [] defs) {
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
          var disableForSetAllFix =
            new UpdateInspectionOptionFix(ExplicitArrayFillingInspection.this,
                                       "mySuggestSetAll",
                                       JavaBundle.message("inspection.explicit.array.filling.no.suggestion.for.set.all"),
                                       false);
          holder.problem(statement, message).highlight(type).range(range).fix(fix).fix(disableForSetAllFix).register();
          return;
        }

        holder.registerProblem(statement, message, type, range, fix);
      }

      private @NotNull TextRange getRange(@NotNull PsiForStatement statement, @NotNull ProblemHighlightType type) {
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

  private static final class ReplaceWithArraysCallFix extends PsiUpdateModCommandQuickFix {
    private final boolean myIsRhsConstant;

    private ReplaceWithArraysCallFix(boolean isRhsConstant) {
      myIsRhsConstant = isRhsConstant;
    }

    @Override
    public @Nls @NotNull String getFamilyName() {
      return JavaBundle.message("inspection.explicit.array.filling.fix.family.name", myIsRhsConstant ? "fill" : "setAll");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiForStatement statement = tryCast(element, PsiForStatement.class);
      if (statement == null) return;
      CountingLoop loop = CountingLoop.from(statement);
      if (loop == null) return;
      PsiAssignmentExpression assignment = ExpressionUtils.getAssignment(ControlFlowUtils.stripBraces(statement.getBody()));
      if (assignment == null) return;
      IndexedContainer container = getContainer(loop, assignment);
      if (container == null) return;
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

    private static @NotNull String getCast(@NotNull PsiElement context, @Nullable PsiType elementType, @Nullable PsiType rType) {
      if (elementType == null || rType == null) return "";
      PsiType assignTo = tryCast(elementType, PsiPrimitiveType.class);
      if (assignTo == null) assignTo = TypeUtils.getObjectType(context);
      return TypeConversionUtil.isAssignable(assignTo, rType) ? "" : "(" + elementType.getCanonicalText() + ")";
    }
  }

  private static @Nullable IndexedContainer getContainer(CountingLoop loop, PsiAssignmentExpression assignment) {
    IndexedContainer container = IndexedContainer.fromLengthExpression(loop.getBound());
    if (container == null) {
      if (!(assignment.getLExpression() instanceof PsiArrayAccessExpression arrayAccessExpression)) {
        return null;
      }
      container = IndexedContainer.arrayContainerWithBound(arrayAccessExpression, loop.getBound());
    }
    return container;
  }
}
