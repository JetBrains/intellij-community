// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.enhancedSwitch;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.JavaFeature;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

import static com.intellij.java.JavaBundle.message;

public final class SwitchLabeledRuleCanBeCodeBlockInspection extends AbstractBaseJavaLocalInspectionTool {
  @Override
  public @NotNull Set<@NotNull JavaFeature> requiredFeatures() {
    return Set.of(JavaFeature.ENHANCED_SWITCH);
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {

    return new JavaElementVisitor() {
      @Override
      public void visitSwitchLabeledRuleStatement(@NotNull PsiSwitchLabeledRuleStatement statement) {
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

      private @NotNull PsiElement getProblemElement(@NotNull PsiSwitchLabeledRuleStatement statement) {
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

  private static class WrapWithCodeBlockFix extends PsiUpdateModCommandQuickFix {
    private final @Nls String myMessage;

    WrapWithCodeBlockFix(boolean isResultExpression) {
      myMessage = message(isResultExpression ? "inspection.switch.labeled.rule.can.be.code.block.fix.expression.name"
                                             : "inspection.switch.labeled.rule.can.be.code.block.fix.statement.name");
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getFamilyName() {
      return myMessage;
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (element instanceof PsiKeyword) {
        element = element.getParent();
      }
      if (element instanceof PsiSwitchLabeledRuleStatement rule) {
        PsiSwitchBlock switchBlock = rule.getEnclosingSwitchBlock();
        PsiStatement body = rule.getBody();

        if (switchBlock instanceof PsiSwitchExpression && body instanceof PsiExpressionStatement statement) {
          wrapExpression(statement);
        }
        else if (body != null) {
          wrapStatement(body);
        }
      }
    }

    private static void wrapExpression(PsiExpressionStatement expressionStatement) {
      CommentTracker tracker = new CommentTracker();
      String valueKeyword = JavaKeywords.YIELD;
      tracker.replaceAndRestoreComments(expressionStatement, "{ " + valueKeyword + " " + tracker.text(expressionStatement) + "\n }");
    }

    private static void wrapStatement(@NotNull PsiStatement statement) {
      CommentTracker tracker = new CommentTracker();
      tracker.replaceAndRestoreComments(statement, "{ " + tracker.text(statement) + "\n }");
    }
  }
}
