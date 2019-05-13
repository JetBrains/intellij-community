// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.enhancedSwitch;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.InspectionsBundle.message;

/**
 * @author Pavel.Dolgov
 */
public class SwitchLabeledRuleCanBeCodeBlockInspection extends LocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!HighlightUtil.Feature.ENHANCED_SWITCH.isAvailable(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }

    return new JavaElementVisitor() {
      @Override
      public void visitSwitchLabeledRuleStatement(PsiSwitchLabeledRuleStatement statement) {
        super.visitSwitchLabeledRuleStatement(statement);

        PsiSwitchBlock switchBlock = statement.getEnclosingSwitchBlock();
        PsiStatement body = statement.getBody();
        if (switchBlock != null && (body instanceof PsiExpressionStatement || body instanceof PsiThrowStatement)) {
          if (switchBlock instanceof PsiSwitchExpression && body instanceof PsiExpressionStatement) {
            registerProblem(statement, true);
          }
          else {
            registerProblem(statement, false);
          }
        }
      }

      private void registerProblem(@NotNull PsiSwitchLabeledRuleStatement statement, boolean isResultExpression) {
        holder.registerProblem(getProblemElement(statement),
                               message(isResultExpression ? "inspection.switch.labeled.rule.can.be.code.block.expression.message"
                                                          : "inspection.switch.labeled.rule.can.be.code.block.statement.message"),
                               new WrapWithCodeBlockFix(isResultExpression));
      }

      @NotNull
      private PsiElement getProblemElement(@NotNull PsiSwitchLabeledRuleStatement statement) {
        if (isOnTheFly) {
          if (InspectionProjectProfileManager.isInformationLevel(getShortName(), statement) ||
              ApplicationManager.getApplication().isUnitTestMode()) {
            return statement;
          }
        }
        return ObjectUtils.notNull(ObjectUtils.tryCast(statement.getFirstChild(), PsiKeyword.class), statement);
      }
    };
  }

  private static class WrapWithCodeBlockFix implements LocalQuickFix {
    private final String myMessage;

    WrapWithCodeBlockFix(boolean isResultExpression) {
      myMessage = message(isResultExpression ? "inspection.switch.labeled.rule.can.be.code.block.fix.expression.name"
                                             : "inspection.switch.labeled.rule.can.be.code.block.fix.statement.name");
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return myMessage;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getStartElement();
      if (element instanceof PsiKeyword) {
        element = element.getParent();
      }
      if (element instanceof PsiSwitchLabeledRuleStatement) {
        PsiSwitchLabeledRuleStatement rule = (PsiSwitchLabeledRuleStatement)element;
        PsiSwitchBlock switchBlock = rule.getEnclosingSwitchBlock();
        PsiStatement body = rule.getBody();

        if (switchBlock instanceof PsiSwitchExpression && body instanceof PsiExpressionStatement) {
          wrapExpression((PsiExpressionStatement)body);
        }
        else if (body != null) {
          wrapStatement(body);
        }
      }
    }

    private static void wrapExpression(PsiExpressionStatement expressionStatement) {
      CommentTracker tracker = new CommentTracker();
      tracker.replaceAndRestoreComments(expressionStatement, "{ break " + tracker.text(expressionStatement) + " }");
    }

    private static void wrapStatement(@NotNull PsiStatement statement) {
      CommentTracker tracker = new CommentTracker();
      tracker.replaceAndRestoreComments(statement, "{ " + tracker.text(statement) + " }");
    }
  }
}
