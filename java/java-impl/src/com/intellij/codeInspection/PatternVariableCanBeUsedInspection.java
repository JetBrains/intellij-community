// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
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
        PsiType castType = cast.getCastType().getType();
        if (castType instanceof PsiPrimitiveType) return;
        if (!variable.getType().equals(castType)) return;
        PsiElement scope = PsiUtil.getVariableCodeBlock(variable, null);
        if (scope == null) return;
        PsiDeclarationStatement declaration = ObjectUtils.tryCast(variable.getParent(), PsiDeclarationStatement.class);
        if (declaration == null) return;
        if (!variable.hasModifierProperty(PsiModifier.FINAL) &&
            !HighlightControlFlowUtil.isEffectivelyFinal(variable, scope, null)) return;
        PsiElement context = declaration;
        PsiElement parent = context.getParent();
        if (parent instanceof PsiCodeBlock) {
          if (processSiblings(identifier, cast, context, parent)) return;
          if (parent.getParent() instanceof PsiBlockStatement) {
            context = parent.getParent();
            parent = context.getParent();
          }
        }
        processParent(identifier, cast, context, parent);
      }

      private void processParent(PsiIdentifier identifier, PsiTypeCastExpression cast, PsiElement context, PsiElement parent) {
        if (parent instanceof PsiIfStatement) {
          PsiIfStatement ifStatement = (PsiIfStatement)parent;
          if (ifStatement.getThenBranch() == context) {
            processCondition(ifStatement.getCondition(), true, cast, identifier);
          }
          else if (ifStatement.getElseBranch() == context) {
            processCondition(ifStatement.getCondition(), false, cast, identifier);
          }
        }
        if (parent instanceof PsiForStatement || parent instanceof PsiWhileStatement) {
          processCondition(((PsiConditionalLoopStatement)parent).getCondition(), true, cast, identifier);
        }
      }

      private boolean processCondition(PsiExpression condition, boolean whenTrue, PsiTypeCastExpression cast, PsiIdentifier identifier) {
        PsiInstanceOfExpression instanceOf = findInstanceOf(condition, cast, whenTrue);
        if (instanceOf != null) {
          String name = identifier.getText();
          holder.registerProblem(identifier,
                                 InspectionGadgetsBundle.message("inspection.pattern.variable.can.be.used.message", name),
                                 new PatternVariableCanBeUsedFix(name, instanceOf));
          return true;
        }
        return false;
      }

      private boolean processSiblings(PsiIdentifier identifier, PsiTypeCastExpression cast, PsiElement context, PsiElement parent) {
        for (PsiElement stmt = context.getPrevSibling(); stmt != null; stmt = stmt.getPrevSibling()) {
          if (stmt instanceof PsiIfStatement) {
            PsiIfStatement ifStatement = (PsiIfStatement)stmt;
            PsiStatement thenBranch = ifStatement.getThenBranch();
            PsiStatement elseBranch = ifStatement.getElseBranch();
            boolean thenCompletes = canCompleteNormally(parent, thenBranch);
            boolean elseCompletes = canCompleteNormally(parent, elseBranch);
            if (thenCompletes != elseCompletes && processCondition(ifStatement.getCondition(), thenCompletes, cast, identifier)) {
              return true;
            }
          }
          if (stmt instanceof PsiWhileStatement || stmt instanceof PsiDoWhileStatement || stmt instanceof PsiForStatement) {
            PsiConditionalLoopStatement loop = (PsiConditionalLoopStatement)stmt;
            if (PsiTreeUtil.processElements(
              loop, e -> !(e instanceof PsiBreakStatement) || ((PsiBreakStatement)e).findExitedStatement() != loop)) {
              if (processCondition(loop.getCondition(), false, cast, identifier)) {
                return true;
              }
            }
          }
          if (isConflictingNameDeclaredInside(identifier, stmt)) return true;
          if (stmt instanceof PsiSwitchLabelStatementBase) break;
        }
        return false;
      }

      private boolean isConflictingNameDeclaredInside(PsiIdentifier identifier, PsiElement statement) {
        class Visitor extends JavaRecursiveElementWalkingVisitor {
          boolean hasConflict = false;
          
          @Override
          public void visitClass(final PsiClass aClass) {}

          @Override
          public void visitVariable(PsiVariable variable) {
            String name = variable.getName();
            if (name != null && identifier.textMatches(name)) {
              hasConflict = true;
              stopWalking();
            }
            super.visitVariable(variable);
          }
        }
        Visitor visitor = new Visitor();
        statement.accept(visitor);
        return visitor.hasConflict;
      }
      
      private boolean canCompleteNormally(@NotNull PsiElement parent, @Nullable PsiStatement statement) {
        if (statement == null) return true;
        ControlFlow flow;
        try {
          flow = ControlFlowFactory.getInstance(holder.getProject()).getControlFlow(
            parent, new LocalsControlFlowPolicy(parent), false, false);
        }
        catch (AnalysisCanceledException e) {
          return true;
        }
        int startOffset = flow.getStartOffset(statement);
        int endOffset = flow.getEndOffset(statement);
        return startOffset != -1 && endOffset != -1 && ControlFlowUtil.canCompleteNormally(flow, startOffset, endOffset);
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
              PsiType type = typeTestPattern.getCheckType().getType();
              PsiType castType = Objects.requireNonNull(cast.getCastType()).getType();
              PsiExpression castOperand = Objects.requireNonNull(cast.getOperand());
              if (typeCompatible(type, castType, castOperand) &&
                  PsiEquivalenceUtil.areElementsEquivalent(instanceOf.getOperand(), castOperand)) {
                return instanceOf;
              }
            }
          }
        }
        return null;
      }

      private boolean typeCompatible(@NotNull PsiType instanceOfType, @NotNull PsiType castType, @NotNull PsiExpression castOperand) {
        if (instanceOfType.equals(castType)) return true;
        if (castType instanceof PsiClassType) {
          PsiClassType rawType = ((PsiClassType)castType).rawType();
          if (instanceOfType.equals(rawType)) {
            return !JavaGenericsUtil.isUncheckedCast(castType, castOperand.getType());
          }
        }
        return false;
      }
    };
  }

  private static class PatternVariableCanBeUsedFix implements LocalQuickFix {
    private final SmartPsiElementPointer<PsiInstanceOfExpression> myInstanceOfPointer;
    private final String myName;

    public PatternVariableCanBeUsedFix(@NotNull String name, @NotNull PsiInstanceOfExpression instanceOf) {
      myName = name;
      myInstanceOfPointer = SmartPointerManager.createPointer(instanceOf);
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("inspection.pattern.variable.can.be.used.fix.name", myName);
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("inspection.pattern.variable.can.be.used.fix.family.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiLocalVariable variable = PsiTreeUtil.getParentOfType(descriptor.getStartElement(), PsiLocalVariable.class);
      if (variable == null) return;
      PsiTypeCastExpression cast = ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(variable.getInitializer()),
                                                       PsiTypeCastExpression.class);
      if (cast == null) return;
      PsiTypeElement typeElement = cast.getCastType();
      if (typeElement == null) return;
      PsiInstanceOfExpression instanceOf = myInstanceOfPointer.getElement();
      if (instanceOf == null) return;
      CommentTracker ct = new CommentTracker();
      ct.replace(instanceOf, ct.text(instanceOf.getOperand()) + " instanceof " + typeElement.getText() + " " + variable.getName());
      ct.deleteAndRestoreComments(variable);
    }
  }
}
