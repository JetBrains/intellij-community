// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.codeInspection.util.LambdaGenerationUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.JavaFeature;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Predicate;

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
        if (!VariableAccessUtils.collectUsedVariables(rValue).contains(loop.getCounter()) &&
            !SideEffectChecker.mayHaveSideEffects(rValue)) {
          Object constValue = ExpressionUtils.computeConstantExpression(rValue);
          if (constValue != null && constValue.equals(PsiTypesUtil.getDefaultValue(assignment.getType()))) {
            holder.registerProblem(statement, getRange(statement, ProblemHighlightType.WARNING),
                                   QuickFixBundle.message("delete.element.fix.text"),
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
      PsiForStatement statement = ObjectUtils.tryCast(descriptor.getStartElement(), PsiForStatement.class);
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
