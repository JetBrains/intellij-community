// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.InstanceOfUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PatternVariableCanBeUsedInspection extends AbstractBaseJavaLocalInspectionTool implements CleanupLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!HighlightingFeature.PATTERNS.isAvailable(holder.getFile())) return PsiElementVisitor.EMPTY_VISITOR;
    return new JavaElementVisitor() {
      @Override
      public void visitLocalVariable(@NotNull PsiLocalVariable variable) {
        PsiIdentifier identifier = variable.getNameIdentifier();
        if (identifier == null) return;
        PsiTypeCastExpression cast = ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(variable.getInitializer()),
                                                         PsiTypeCastExpression.class);
        if (cast == null || cast.getCastType() == null) return;
        PsiExpression operand = cast.getOperand();
        if (operand == null) return;
        PsiType castType = cast.getCastType().getType();
        if (castType instanceof PsiPrimitiveType) return;
        if (!variable.getType().equals(castType)) return;
        PsiType operandType = operand.getType();
        if (operandType == null || castType.isAssignableFrom(operandType)) return;
        PsiElement scope = PsiUtil.getVariableCodeBlock(variable, null);
        if (scope == null) return;
        PsiDeclarationStatement declaration = ObjectUtils.tryCast(variable.getParent(), PsiDeclarationStatement.class);
        if (declaration == null) return;
        if (!PsiUtil.isLanguageLevel16OrHigher(holder.getFile()) &&
            !variable.hasModifierProperty(PsiModifier.FINAL) &&
            !HighlightControlFlowUtil.isEffectivelyFinal(variable, scope, null)) return;
        PsiInstanceOfExpression instanceOf = InstanceOfUtils.findPatternCandidate(cast);
        if (instanceOf != null) {
          PsiPatternVariable existingPatternVariable = null;
          PsiPattern pattern = instanceOf.getPattern();
          if (pattern instanceof PsiTypeTestPattern) {
            existingPatternVariable = ((PsiTypeTestPattern)pattern).getPatternVariable();
          }
          String name = identifier.getText();
          if (existingPatternVariable != null) {
            holder.registerProblem(identifier,
                                   InspectionGadgetsBundle.message("inspection.pattern.variable.can.be.used.existing.message", 
                                                                   existingPatternVariable.getName(), name),
                                   new ExistingPatternVariableCanBeUsedFix(name, existingPatternVariable));
          } else {
            holder.registerProblem(identifier,
                                   InspectionGadgetsBundle.message("inspection.pattern.variable.can.be.used.message", name),
                                   new PatternVariableCanBeUsedFix(name, instanceOf));
          }
        }
      }

    };
  }
  
  private static class ExistingPatternVariableCanBeUsedFix implements LocalQuickFix {
    private final @NotNull String myName;
    private final @NotNull String myPatternName;

    private ExistingPatternVariableCanBeUsedFix(@NotNull String name, @NotNull PsiPatternVariable existingVariable) {
      myName = name;
      myPatternName = existingVariable.getName();
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("inspection.pattern.variable.can.be.used.existing.fix.name", myName, myPatternName);
    }
    
    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("inspection.pattern.variable.can.be.used.existing.fix.family.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiLocalVariable variable = PsiTreeUtil.getParentOfType(descriptor.getStartElement(), PsiLocalVariable.class);
      if (variable == null) return;
      List<PsiReferenceExpression> references =
        VariableAccessUtils.getVariableReferences(variable, PsiUtil.getVariableCodeBlock(variable, null));
      for (PsiReferenceExpression ref : references) {
        ExpressionUtils.bindReferenceTo(ref, myPatternName);
      }
      new CommentTracker().deleteAndRestoreComments(variable);
    }
  }

  private static class PatternVariableCanBeUsedFix implements LocalQuickFix {
    private final SmartPsiElementPointer<PsiInstanceOfExpression> myInstanceOfPointer;
    private final String myName;

    private PatternVariableCanBeUsedFix(@NotNull String name, @NotNull PsiInstanceOfExpression instanceOf) {
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
      PsiModifierList modifierList = variable.getModifierList();
      String modifiers = modifierList == null || modifierList.getTextLength() == 0 || !PsiUtil.isLanguageLevel16OrHigher(variable) ? 
                         "" : modifierList.getText() + " ";
      ct.replace(instanceOf, ct.text(instanceOf.getOperand()) + 
                             " instanceof " + modifiers + typeElement.getText() + " " + variable.getName());
      ct.deleteAndRestoreComments(variable);
    }

    @Override
    public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
      PsiInstanceOfExpression instanceOf = myInstanceOfPointer.getElement();
      return instanceOf == null ? null : new PatternVariableCanBeUsedFix(myName, PsiTreeUtil
        .findSameElementInCopy(instanceOf, target));
    }
  }
}
