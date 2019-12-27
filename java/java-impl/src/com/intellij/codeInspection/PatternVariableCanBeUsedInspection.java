// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class PatternVariableCanBeUsedInspection extends AbstractBaseJavaLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!HighlightUtil.Feature.PATTERNS.isAvailable(holder.getFile())) return PsiElementVisitor.EMPTY_VISITOR;
    return new JavaElementVisitor() {
      @Override
      public void visitLocalVariable(PsiLocalVariable variable) {
        PsiIdentifier identifier = variable.getNameIdentifier();
        if (identifier == null) return;
        PsiTypeCastExpression cast = ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(variable.getInitializer()),
                                                         PsiTypeCastExpression.class);
        if (cast == null || cast.getOperand() == null || cast.getCastType() == null) return;
        PsiType castType = cast.getType();
        if (castType instanceof PsiPrimitiveType) return;
        PsiElement scope = PsiUtil.getVariableCodeBlock(variable, null);
        if (scope == null) return;
        PsiDeclarationStatement declaration = ObjectUtils.tryCast(variable.getParent(), PsiDeclarationStatement.class);
        if (declaration == null) return;
        PsiElement context = declaration;
        PsiElement parent = context.getParent();
        if (parent instanceof PsiCodeBlock && parent.getParent() instanceof PsiBlockStatement) {
          context = parent.getParent();
          parent = context.getParent();
        }
        if (parent instanceof PsiIfStatement) {
          PsiIfStatement ifStatement = (PsiIfStatement)parent;
          boolean whenTrue;
          if (ifStatement.getThenBranch() == context) {
            whenTrue = true;
          }
          else if (ifStatement.getElseBranch() == context) {
            whenTrue = false;
          }
          else {
            return;
          }
          PsiInstanceOfExpression instanceOf = findInstanceOf(ifStatement.getCondition(), cast, whenTrue);
          if (instanceOf != null) {
            holder.registerProblem(identifier, "Variable can be replaced with pattern variable",
                                   new PatternVariableCanBeUsedFix(instanceOf));
          }
        }
      }

      private PsiInstanceOfExpression findInstanceOf(PsiExpression condition, PsiTypeCastExpression cast, boolean whenTrue) {
        if (condition instanceof PsiParenthesizedExpression) {
          return findInstanceOf(((PsiParenthesizedExpression)condition).getExpression(), cast, whenTrue);
        }
        if (BoolUtils.isNegation(condition)) {
          return findInstanceOf(BoolUtils.getNegated(condition), cast, !whenTrue);
        }
        if (condition instanceof PsiPolyadicExpression) {
          PsiPolyadicExpression polyadic = (PsiPolyadicExpression)condition;
          IElementType tokenType = polyadic.getOperationTokenType();
          if (tokenType == JavaTokenType.ANDAND && whenTrue ||
              tokenType == JavaTokenType.OROR && !whenTrue) {
            for (PsiExpression operand : polyadic.getOperands()) {
              PsiInstanceOfExpression result = findInstanceOf(operand, cast, whenTrue);
              if (result != null) {
                return result;
              }
            }
          }
        }
        if (condition instanceof PsiInstanceOfExpression && whenTrue) {
          PsiInstanceOfExpression instanceOf = (PsiInstanceOfExpression)condition;
          PsiPattern pattern = instanceOf.getPattern();
          if (pattern instanceof PsiTypeTestPattern) {
            PsiTypeTestPattern typeTestPattern = (PsiTypeTestPattern)pattern;
            PsiPatternVariable variable = typeTestPattern.getPatternVariable();
            if (variable == null) {
              PsiTypeElement type = typeTestPattern.getCheckType();
              if (PsiEquivalenceUtil.areElementsEquivalent(type, Objects.requireNonNull(cast.getCastType())) &&
                  PsiEquivalenceUtil.areElementsEquivalent(instanceOf.getOperand(), Objects.requireNonNull(cast.getOperand()))) {
                return instanceOf;
              }
            }
          }
        }
        return null;
      }
    };
  }

  private static class PatternVariableCanBeUsedFix implements LocalQuickFix {
    private final SmartPsiElementPointer<PsiInstanceOfExpression> myInstanceOfPointer;

    public PatternVariableCanBeUsedFix(@NotNull PsiInstanceOfExpression instanceOf) {
      myInstanceOfPointer = SmartPointerManager.createPointer(instanceOf);
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace with pattern variable";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiLocalVariable variable = PsiTreeUtil.getParentOfType(descriptor.getStartElement(), PsiLocalVariable.class);
      if (variable == null) return;
      PsiInstanceOfExpression instanceOf = myInstanceOfPointer.getElement();
      if (instanceOf == null) return;
      CommentTracker ct = new CommentTracker();
      ct.replace(instanceOf, ct.text(instanceOf) + " " + variable.getName());
      ct.deleteAndRestoreComments(variable);
    }
  }
}
