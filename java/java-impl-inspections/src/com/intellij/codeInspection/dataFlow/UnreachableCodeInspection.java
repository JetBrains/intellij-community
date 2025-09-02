// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.SuppressionUtil;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.java.JavaBundle;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class UnreachableCodeInspection extends AbstractBaseJavaLocalInspectionTool {
  public boolean ignoreTrivialReturns = true;
  public boolean respectConstantValueSuppression = true;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreTrivialReturns", JavaBundle.message("inspection.data.flow.unreachable.code.option.ignore.trivial.name"))
        .description(HtmlChunk.raw(JavaBundle.message("inspection.data.flow.unreachable.code.option.ignore.trivial.description"))),
      checkbox("respectConstantValueSuppression", JavaBundle.message("inspection.data.flow.unreachable.code.option.respect.suppression.name"))
        .description(HtmlChunk.raw(JavaBundle.message("inspection.data.flow.unreachable.code.option.respect.suppression.description")))
      );
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitClass(@NotNull PsiClass aClass) {
        processElement(aClass);
      }

      @Override
      public void visitMethod(@NotNull PsiMethod method) {
        if (!method.isConstructor()) {
          processElement(method);
        }
      }

      private void processElement(@NotNull PsiElement element) {
        CommonDataflow.DataflowResult result = CommonDataflow.getDataflowResult(element);
        if (result == null) return;

        PsiFile file = holder.getFile();
        for (TextRange range : result.getUnreachableRanges()) {
          PsiElement psiElement = file.findElementAt(range.getStartOffset());
          while (psiElement != null && !psiElement.getTextRange().contains(range)) {
            psiElement = psiElement.getParent();
          }
          if (psiElement != null) {
            if (psiElement.getTextRange().equals(range) && shouldSuppress(psiElement)) continue;
            if (element != StreamEx.iterate(psiElement, Objects::nonNull, PsiElement::getParent)
              .findFirst(e -> e instanceof PsiClass || 
                              e instanceof PsiMethod method && !method.isConstructor()).orElse(null)) {
              // Nested method or class: will be reported for the corresponding anchor
              continue;
            }
            holder.registerProblem(psiElement, range.shiftLeft(psiElement.getTextRange().getStartOffset()),
                                   JavaBundle.message("inspection.data.flow.unreachable.code.display.name"));
          }
        }
      }

      private boolean shouldSuppress(PsiElement psiElement) {
        if (psiElement instanceof PsiExpressionStatement statement && isTrivialExpression(statement.getExpression()) &&
            psiElement.getParent() instanceof PsiSwitchLabeledRuleStatement rule && rule.isDefaultCase()) {
          // Default case in switch might be required by compiler
          return true;
        }
        Condition condition = findCondition(psiElement); 
        if (condition != null) {
          if (ConstantValueInspection.isFlagCheck(condition.expression)) {
            return true;
          }
          if (respectConstantValueSuppression &&
              (SuppressionUtil.isSuppressed(condition.expression, "ConstantValue") ||
               SuppressionUtil.isSuppressed(condition.expression, "ConstantConditions"))) {
            return true;
          }
        }
        if (psiElement instanceof PsiCodeBlock block) {
          if (block.getParent() instanceof PsiCatchSection catchSection) {
            PsiType type = catchSection.getCatchType();
            if (mayCover(type, "java.lang.LinkageError") ||
                mayCover(type, "java.lang.VirtualMachineError")) {
              return true;
            }
          }
          else {
            if (block.getStatementCount() == 1) {
              psiElement = block.getStatements()[0];
            }
          }
        }
        return psiElement instanceof PsiThrowStatement || ignoreTrivialReturns && isTrivialReturn(psiElement);
      }
      
      record Condition(boolean value, @NotNull PsiExpression expression) {}
      
      private static @Nullable Condition findCondition(PsiElement deadCode) {
        if (deadCode instanceof PsiExpression) {
          if (deadCode.getParent() instanceof PsiConditionalExpression ternary) {
            if (ternary.getElseExpression() == deadCode) {
              return new Condition(false, ternary.getCondition());
            }
            else if (ternary.getThenExpression() == deadCode) {
              return new Condition(true, ternary.getCondition());
            }
          }
        }
        if (deadCode instanceof PsiCodeBlock block && block.getParent() instanceof PsiBlockStatement blockStatement) {
          deadCode = blockStatement;
        }
        if (deadCode.getParent() instanceof PsiIfStatement ifStatement) {
          PsiExpression condition = ifStatement.getCondition();
          if (condition != null) {
            if (ifStatement.getThenBranch() == deadCode) {
              return new Condition(true, condition);
            }
            if (ifStatement.getElseBranch() == deadCode) {
              return new Condition(false, condition);
            }
          }
        }
        if (deadCode instanceof PsiStatement statement) {
          if (PsiTreeUtil.skipWhitespacesAndCommentsBackward(statement) instanceof PsiIfStatement prevIf) {
            if (prevIf.getElseBranch() == null && !ControlFlowUtils.statementMayCompleteNormally(prevIf.getThenBranch())) {
              PsiExpression condition = prevIf.getCondition();
              if (condition != null) {
                return new Condition(false, condition);
              }
            }
          }
        }
        return null;
      }

      private static boolean mayCover(PsiType type, String exceptionFqn) {
        if (type == null) return false;
        if (type instanceof PsiDisjunctionType disjunctionType) {
          for (PsiType disjunction : disjunctionType.getDisjunctions()) {
            if (mayCover(disjunction, exceptionFqn)) return true;
          }
          return false;
        }
        if (type instanceof PsiClassType classType) {
          PsiClass psiClass = classType.resolve();
          if (psiClass == null) return false;
          if (InheritanceUtil.isInheritor(psiClass, exceptionFqn)) return true;
          PsiClass exceptionClass = JavaPsiFacade.getInstance(psiClass.getProject()).findClass(exceptionFqn, psiClass.getResolveScope());
          if (exceptionClass != null && exceptionClass.isInheritor(psiClass, true)) return true;
        }
        return false;
      }

      private static boolean isTrivialReturn(@NotNull PsiElement element) {
        if (element instanceof PsiBreakStatement || element instanceof PsiContinueStatement) return true;
        if (element instanceof PsiYieldStatement yieldStatement) {
          return isTrivialExpression(yieldStatement.getExpression());
        }
        if (element instanceof PsiReturnStatement returnStatement) {
          return isTrivialExpression(returnStatement.getReturnValue());
        }
        return false;
      }

      private static boolean isTrivialExpression(PsiExpression expression) {
        if (expression == null) return true;
        if (expression instanceof PsiThisExpression thisExpression && thisExpression.getQualifier() == null) return true;
        if (expression instanceof PsiLiteralExpression literal) {
          Object value = literal.getValue();
          if (value instanceof Boolean || "".equals(value) || value instanceof Number number && number.doubleValue() == 0.0) {
            return true;
          }
          if (literal.textMatches(JavaKeywords.NULL)) return true;
        }
        return false;
      }
    };
  }
}
