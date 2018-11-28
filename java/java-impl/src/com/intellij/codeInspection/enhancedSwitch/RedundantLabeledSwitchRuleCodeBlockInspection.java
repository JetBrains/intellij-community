// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.enhancedSwitch;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInspection.InspectionsBundle.message;

/**
 * @author Pavel.Dolgov
 */
public class RedundantLabeledSwitchRuleCodeBlockInspection extends LocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.getLanguageLevel(holder.getFile()).isAtLeast(LanguageLevel.JDK_12_PREVIEW)) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitSwitchLabeledRuleStatement(PsiSwitchLabeledRuleStatement statement) {
        super.visitSwitchLabeledRuleStatement(statement);

        PsiStatement bodyStatement = unwrapSingleStatementCodeBlock(statement.getBody());
        if (bodyStatement instanceof PsiBreakStatement) {
          if (((PsiBreakStatement)bodyStatement).getValueExpression() != null) {
            registerProblem(statement);
          }
        }
        else if (bodyStatement instanceof PsiThrowStatement || bodyStatement instanceof PsiExpressionStatement) {
          registerProblem(statement);
        }
      }

      public void registerProblem(PsiSwitchLabeledRuleStatement statement) {
        holder.registerProblem(ObjectUtils.notNull(ObjectUtils.tryCast(statement.getFirstChild(), PsiKeyword.class), statement),
                               message("inspection.labeled.switch.rule.redundant.code.block.message"),
                               new UnwrapCodeBlockFix());
      }
    };
  }

  @Nullable
  private static PsiStatement unwrapSingleStatementCodeBlock(@Nullable PsiStatement statement) {
    if (statement instanceof PsiBlockStatement) {
      PsiCodeBlock block = ((PsiBlockStatement)statement).getCodeBlock();
      PsiStatement firstStatement = PsiTreeUtil.getNextSiblingOfType(block.getLBrace(), PsiStatement.class);
      if (firstStatement != null && PsiTreeUtil.getNextSiblingOfType(firstStatement, PsiStatement.class) == null) {
        return firstStatement;
      }
    }
    return null;
  }

  private static class UnwrapCodeBlockFix implements LocalQuickFix {
    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return message("inspection.labeled.switch.rule.redundant.code.fix.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getStartElement();
      if (element instanceof PsiKeyword) {
        element = element.getParent();
      }
      if (element instanceof PsiSwitchLabeledRuleStatement) {
        PsiStatement body = ((PsiSwitchLabeledRuleStatement)element).getBody();

        PsiStatement bodyStatement = unwrapSingleStatementCodeBlock(body);
        if (bodyStatement instanceof PsiBreakStatement) {
          unwrapBreakValue(body, (PsiBreakStatement)bodyStatement);
        }
        else if (bodyStatement instanceof PsiThrowStatement || bodyStatement instanceof PsiExpressionStatement) {
          unwrap(body, bodyStatement);
        }
      }
    }

    private static void unwrapBreakValue(PsiStatement body, PsiBreakStatement breakStatement) {
      PsiExpression valueExpression = breakStatement.getValueExpression();
      if (valueExpression != null) {
        CommentTracker tracker = new CommentTracker();
        tracker.markUnchanged(valueExpression);
        tracker.replaceAndRestoreComments(body, valueExpression.getText() + ';');
      }
    }

    private static void unwrap(PsiStatement body, PsiStatement bodyStatement) {
      CommentTracker tracker = new CommentTracker();
      tracker.markUnchanged(bodyStatement);
      tracker.replaceAndRestoreComments(body, bodyStatement);
    }
  }
}
