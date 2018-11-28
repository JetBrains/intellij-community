// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.enhancedSwitch;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
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
    if (!PsiUtil.getLanguageLevel(holder.getFile()).isAtLeast(LanguageLevel.JDK_12_PREVIEW)) {
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

      private void registerProblem(PsiSwitchLabeledRuleStatement statement, boolean isExpressionResult) {
        holder.registerProblem(ObjectUtils.notNull(ObjectUtils.tryCast(statement.getFirstChild(), PsiKeyword.class), statement),
                               message(isExpressionResult ? "inspection.switch.labeled.rule.can.be.code.block.expression.message"
                                                          : "inspection.switch.labeled.rule.can.be.code.block.statement.message"),
                               new WrapWithCodeBlockFix(isExpressionResult));
      }
    };
  }

  private static class WrapWithCodeBlockFix implements LocalQuickFix {
    private final String myMessage;

    WrapWithCodeBlockFix(boolean isExpressionResult) {
      myMessage = message(isExpressionResult ? "inspection.switch.labeled.rule.can.be.code.block.fix.expression.name"
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
      PsiExpression expression = expressionStatement.getExpression();
      CommentTracker tracker = new CommentTracker();
      tracker.markUnchanged(expression);
      tracker.replaceAndRestoreComments(expressionStatement, "{ break " + expression.getText() + "; }");
    }

    private static void wrapStatement(@NotNull PsiStatement statement) {
      CommentTracker tracker = new CommentTracker();
      tracker.markUnchanged(statement);
      tracker.replaceAndRestoreComments(statement, "{ " + statement.getText() + " }");
    }
  }
}
