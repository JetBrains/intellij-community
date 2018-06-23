// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.java18api;

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.util.LambdaGenerationUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.JavaFeature;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

public class Java8ArraySetAllInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance(Java8ArraySetAllInspection.class);

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!JavaFeature.ADVANCED_COLLECTIONS_API.isFeatureSupported(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitForStatement(PsiForStatement statement) {
        super.visitForStatement(statement);
        CountingLoop loop = CountingLoop.from(statement);
        if (loop == null || loop.isIncluding() || loop.isDescending()) return;
        IndexedContainer container = IndexedContainer.fromLengthExpression(loop.getBound());
        if (container == null || !(container.getQualifier().getType() instanceof PsiArrayType)) return;
        if (!StreamApiUtil.isSupportedStreamElement(container.getElementType())) return;
        PsiAssignmentExpression assignment = ExpressionUtils.getAssignment(ControlFlowUtils.stripBraces(statement.getBody()));
        if (assignment == null) return;
        PsiExpression index = container.extractIndexFromGetExpression(assignment.getLExpression());
        if (!ExpressionUtils.isReferenceTo(index, loop.getCounter())) return;
        if (!LambdaGenerationUtil.canBeUncheckedLambda(assignment.getRExpression(), Predicate.isEqual(loop.getCounter()))) return;
        PsiStatement initialization = statement.getInitialization();
        LOG.assertTrue(initialization != null);
        TextRange range = TextRange.from(initialization.getStartOffsetInParent(), initialization.getTextLength());
        boolean wholeStatement = isOnTheFly && InspectionProjectProfileManager.isInformationLevel(getShortName(), statement);
        PsiJavaToken rParenth = statement.getRParenth();
        if (wholeStatement && rParenth != null) {
          range = new TextRange(0, rParenth.getStartOffsetInParent() + 1);
        }
        holder.registerProblem(statement, range, InspectionsBundle.message("inspection.replace.loop.with.arrays.setall.message"),
                               new ReplaceWithArraysSetAllFix());
      }
    };
  }

  private static class ReplaceWithArraysSetAllFix implements LocalQuickFix {
    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionsBundle.message("inspection.replace.loop.with.arrays.setall.fix.family.name");
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
      String replacement = CommonClassNames.JAVA_UTIL_ARRAYS + ".setAll(" +
                           ct.text(container.getQualifier()) + ", " + loop.getCounter().getName() + "->" + ct.text(rValue) + ");";
      PsiElement result = ct.replaceAndRestoreComments(statement, replacement);
      LambdaCanBeMethodReferenceInspection.replaceAllLambdasWithMethodReferences(result);
      result = JavaCodeStyleManager.getInstance(project).shortenClassReferences(result);
      CodeStyleManager.getInstance(project).reformat(result);
    }
  }
}
