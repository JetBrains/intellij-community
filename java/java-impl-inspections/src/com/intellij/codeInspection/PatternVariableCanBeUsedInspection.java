// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightRecordMethod;
import com.intellij.psi.util.JavaPsiPatternUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
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
      public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
        if (!HighlightingFeature.PATTERN_GUARDS_AND_RECORD_PATTERNS.isAvailable(holder.getFile())) {
          return;
        }
        PsiElement resolved = expression.resolve();
        if (resolved instanceof PsiPatternVariable variable && variable.getPattern() instanceof PsiDeconstructionPattern deconstruction) {
          Ref<Result> resultRef = Ref.create();
          findMethodCallAndCorrespondingVariable(expression, deconstruction, resultRef);
          if (resultRef.isNull()) return;
          Result result = resultRef.get();
          PsiMethodCallExpression call = result.call();
          PsiPatternVariable existingPatternVariable = result.existingPatternVariable();
          PsiElement parent = PsiUtil.skipParenthesizedExprUp(call.getParent());
          String patternName = existingPatternVariable.getName();
          if (parent instanceof PsiLocalVariable localVariable) {
            String name = localVariable.getName();
            LocalQuickFix fix = new ExistingPatternVariableCanBeUsedFix(name, existingPatternVariable);
            holder.registerProblem(call, InspectionGadgetsBundle.message("inspection.pattern.variable.can.be.used.existing.message",
                                                                         patternName, name), fix);
          }
          else {
            LocalQuickFix fix = new ExistingPatternVariableCanBeUsedFix(call.getText(), existingPatternVariable);
            holder.registerProblem(call, InspectionGadgetsBundle.message("inspection.pattern.variable.can.be.used.existing.message",
                                                                         patternName, call.getText()), fix);
          }
        }
      }

      private static void findMethodCallAndCorrespondingVariable(@NotNull PsiExpression expression,
                                                                 @NotNull PsiDeconstructionPattern deconstruction,
                                                                 @NotNull Ref<Result> resultRef) {
        PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
        if (!(parent instanceof PsiReferenceExpression)) return;
        PsiElement grandParent = parent.getParent();
        if (grandParent instanceof PsiMethodCallExpression call && call.resolveMethod() instanceof LightRecordMethod recordMethod) {
          PsiClass aClass = recordMethod.getContainingClass();
          PsiRecordComponent[] recordComponents = aClass.getRecordComponents();
          PsiRecordComponent recordComponent = recordMethod.getRecordComponent();
          int index = ArrayUtil.find(recordComponents, recordComponent);
          if (index == -1) return;

          PsiPattern[] deconstructionComponents = deconstruction.getDeconstructionList().getDeconstructionComponents();
          if (index >= deconstructionComponents.length) return;
          PsiPattern deconstructionComponent = deconstructionComponents[index];
          PsiType componentType = JavaPsiPatternUtil.getPatternType(deconstructionComponent);
          if (componentType == null || !componentType.equals(recordComponent.getType())) return;
          PsiPatternVariable variable = JavaPsiPatternUtil.getPatternVariable(deconstructionComponent);
          if (variable != null) {
            resultRef.set(new Result(call, variable));
          }
          if (deconstructionComponent instanceof PsiDeconstructionPattern) {
            findMethodCallAndCorrespondingVariable(call, (PsiDeconstructionPattern)deconstructionComponent, resultRef);
          }
        }
      }

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
          PsiPattern pattern = instanceOf.getPattern();
          PsiPatternVariable existingPatternVariable = JavaPsiPatternUtil.getPatternVariable(pattern);
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

  private record Result(PsiMethodCallExpression call, PsiPatternVariable existingPatternVariable) {
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
      if (!myName.endsWith("()")) {
        PsiLocalVariable variable = PsiTreeUtil.getParentOfType(descriptor.getStartElement(), PsiLocalVariable.class);
        if (variable == null) return;
        List<PsiReferenceExpression> references =
          VariableAccessUtils.getVariableReferences(variable, PsiUtil.getVariableCodeBlock(variable, null));
        for (PsiReferenceExpression ref : references) {
          ExpressionUtils.bindReferenceTo(ref, myPatternName);
        }
        new CommentTracker().deleteAndRestoreComments(variable);
      }
      else {
        new CommentTracker().replace(descriptor.getStartElement(), myPatternName);
      }
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
      String deconstructionList =
        instanceOf.getPattern() instanceof PsiDeconstructionPattern deconstruction ? deconstruction.getDeconstructionList().getText() : "";
      ct.replace(instanceOf, ct.text(instanceOf.getOperand()) +
                             " instanceof " + modifiers + typeElement.getText() + deconstructionList + " " + variable.getName());
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
