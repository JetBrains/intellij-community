// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.light.LightRecordMethod;
import com.intellij.psi.impl.source.tree.JavaSharedImplUtil;
import com.intellij.psi.util.JavaPsiPatternUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class PatternVariableCanBeUsedInspection extends AbstractBaseJavaLocalInspectionTool implements CleanupLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!HighlightingFeature.PATTERNS.isAvailable(holder.getFile())) return PsiElementVisitor.EMPTY_VISITOR;
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
        if (!HighlightingFeature.PATTERN_GUARDS_AND_RECORD_PATTERNS.isAvailable(holder.getFile())) return;
        PsiTypeCastExpression qualifier = getQualifierReferenceExpression(call);
        if (qualifier == null) return;
        PsiInstanceOfExpression candidate = InstanceOfUtils.findPatternCandidate(qualifier);
        if (candidate == null) return;
        PsiPrimaryPattern pattern = candidate.getPattern();
        if (pattern instanceof PsiDeconstructionPattern deconstruction) {
          PsiPatternVariable existingPatternVariable = findExistingPatternVariable(qualifier, deconstruction, call);
          if (existingPatternVariable == null) return;
          if (!isFinalOrEffectivelyFinal(existingPatternVariable)) return;
          String patternName = existingPatternVariable.getName();
          if (PsiUtil.skipParenthesizedExprUp(call.getParent()) instanceof PsiLocalVariable localVariable &&
              canReplaceLocalVariableWithPatternVariable(localVariable, existingPatternVariable)) {
            String name = localVariable.getName();
            LocalQuickFix fix = new ExistingPatternVariableCanBeUsedFix(name, existingPatternVariable);
            holder.registerProblem(call, InspectionGadgetsBundle.message("inspection.pattern.variable.can.be.used.existing.message",
                                                                         patternName, name), fix);
          }
          else {
            String callText = call.getText();
            LocalQuickFix fix = new ExistingPatternVariableCanBeUsedFix(callText, existingPatternVariable);
            holder.registerProblem(call, InspectionGadgetsBundle.message("inspection.pattern.variable.can.be.used.existing.message",
                                                                         patternName, callText), fix);
          }
        }
      }

      private static boolean isFinalOrEffectivelyFinal(@NotNull PsiPatternVariable variable) {
        return (!(variable.getPattern() instanceof PsiDeconstructionPattern) && variable.hasModifierProperty(PsiModifier.FINAL)) ||
               !VariableAccessUtils.variableIsAssigned(variable, variable.getDeclarationScope());
      }

      private static boolean canReplaceLocalVariableWithPatternVariable(@NotNull PsiLocalVariable localVariable,
                                                                        @NotNull PsiPatternVariable patternVariable) {
        PsiElement scope = PsiUtil.getVariableCodeBlock(localVariable, null);
        if (scope == null) return false;
        return localVariable.hasModifierProperty(PsiModifier.FINAL) ||
               !patternVariable.hasModifierProperty(PsiModifier.FINAL) ||
               HighlightControlFlowUtil.isEffectivelyFinal(localVariable, scope, null);
      }

      @Nullable
      private static PsiTypeCastExpression getQualifierReferenceExpression(@NotNull PsiMethodCallExpression call) {
        while (true) {
          if (!call.getArgumentList().isEmpty()) return null;
          PsiExpression qualifier = PsiUtil.skipParenthesizedExprDown(call.getMethodExpression().getQualifierExpression());
          PsiMethodCallExpression qualifierMethodCall = ObjectUtils.tryCast(qualifier, PsiMethodCallExpression.class);
          if (qualifierMethodCall == null) return ObjectUtils.tryCast(qualifier, PsiTypeCastExpression.class);
          call = qualifierMethodCall;
        }
      }

      private static PsiPatternVariable findExistingPatternVariable(@NotNull PsiExpression currentQualifier,
                                                                    @NotNull PsiDeconstructionPattern currentDeconstruction,
                                                                    @NotNull PsiMethodCallExpression call) {
        PsiElement parent = PsiUtil.skipParenthesizedExprUp(currentQualifier.getParent());
        if (!(parent instanceof PsiReferenceExpression)) return null;
        PsiElement grandParent = parent.getParent();
        if (grandParent instanceof PsiMethodCallExpression currentCall &&
            currentCall.resolveMethod() instanceof LightRecordMethod recordMethod) {
          PsiClass aClass = recordMethod.getContainingClass();
          PsiRecordComponent[] recordComponents = aClass.getRecordComponents();
          PsiRecordComponent recordComponent = recordMethod.getRecordComponent();
          int index = ArrayUtil.find(recordComponents, recordComponent);
          if (index < 0) return null;
          PsiPattern[] deconstructionComponents = currentDeconstruction.getDeconstructionList().getDeconstructionComponents();
          if (index >= deconstructionComponents.length) return null;
          PsiPattern deconstructionComponent = deconstructionComponents[index];
          PsiType componentType = JavaPsiPatternUtil.getPatternType(deconstructionComponent);
          if (componentType == null || !componentType.equals(recordComponent.getType())) return null;
          PsiPatternVariable variable = JavaPsiPatternUtil.getPatternVariable(deconstructionComponent);
          if (currentCall.equals(call)) return variable;
          if (deconstructionComponent instanceof PsiDeconstructionPattern deconstruction) {
            return findExistingPatternVariable(currentCall, deconstruction, call);
          }
        }
        return null;
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
        PsiInstanceOfExpression instanceOf = InstanceOfUtils.findPatternCandidate(cast, variable);
        if (instanceOf != null) {
          PsiPattern pattern = instanceOf.getPattern();
          PsiPatternVariable existingPatternVariable = JavaPsiPatternUtil.getPatternVariable(pattern);
          //it is a deconstruction pattern and we can't add new variable here
          if (pattern != null && existingPatternVariable == null) {
            return;
          }
          String name = identifier.getText();
          if (existingPatternVariable != null) {
            if (!canReplaceLocalVariableWithPatternVariable(variable, existingPatternVariable) ||
                !isFinalOrEffectivelyFinal(existingPatternVariable)) {
              return;
            }
            holder.registerProblem(identifier,
                                   InspectionGadgetsBundle.message("inspection.pattern.variable.can.be.used.existing.message",
                                                                   existingPatternVariable.getName(), name),
                                   new ExistingPatternVariableCanBeUsedFix(name, existingPatternVariable));
          } else {
            if (!isOnTheFly && InstanceOfUtils.hasConflictingDeclaredNames(variable, instanceOf)) {
              return;
            }
            holder.registerProblem(identifier,
                                   InspectionGadgetsBundle.message("inspection.pattern.variable.can.be.used.message", name),
                                   new PatternVariableCanBeUsedFix(name, instanceOf));
          }
        }
      }

    };
  }

  private static class ExistingPatternVariableCanBeUsedFix extends PsiUpdateModCommandQuickFix {
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
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (!myName.endsWith("()")) {
        PsiLocalVariable variable = PsiTreeUtil.getParentOfType(element, PsiLocalVariable.class);
        if (variable == null) return;
        if (VariableAccessUtils.variableIsAssigned(variable)) {
          new CommentTracker().replace(element, myPatternName);
          return;
        }
        List<PsiReferenceExpression> references =
          VariableAccessUtils.getVariableReferences(variable, PsiUtil.getVariableCodeBlock(variable, null));
        for (PsiReferenceExpression ref : references) {
          ExpressionUtils.bindReferenceTo(ref, myPatternName);
        }
        new CommentTracker().deleteAndRestoreComments(variable);
      }
      else {
        new CommentTracker().replace(element, myPatternName);
      }
    }
  }

  private static class PatternVariableCanBeUsedFix extends PsiUpdateModCommandQuickFix {
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
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (!(element.getParent()  instanceof PsiLocalVariable variable)) return;
      PsiTypeCastExpression cast = ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(variable.getInitializer()),
                                                       PsiTypeCastExpression.class);
      if (cast == null) return;
      PsiTypeElement typeElement = cast.getCastType();
      if (typeElement == null) return;
      PsiInstanceOfExpression instanceOf = PsiTreeUtil.findSameElementInCopy(myInstanceOfPointer.getElement(), element.getContainingFile());
      if (instanceOf == null) return;
      PsiTypeElement instanceOfType = instanceOf.getCheckType();
      if (instanceOfType != null && instanceOfType.getType() instanceof PsiClassType classType && !classType.isRaw()) {
        typeElement = instanceOfType;
      }
      CommentTracker ct = new CommentTracker();
      StringBuilder text = new StringBuilder(ct.text(instanceOf.getOperand()));
      text.append(" instanceof ");
      PsiModifierList modifierList = variable.getModifierList();
      JavaCodeStyleSettings codeStyleSettings = JavaCodeStyleSettings.getInstance(variable.getContainingFile());
      if (modifierList != null && modifierList.getTextLength() > 0) {
        modifierList.setModifierProperty(PsiModifier.FINAL, codeStyleSettings.GENERATE_FINAL_LOCALS);
        text.append(ct.text(modifierList)).append(' ');
      }
      else if (codeStyleSettings.GENERATE_FINAL_LOCALS) {
        text.append("final ");
      }
      text.append(typeElement.getText()).append(' ');
      if (instanceOf.getPattern() instanceof PsiDeconstructionPattern) {
        return;
      }
      text.append(variable.getName());
      PsiElement replaced = ct.replace(instanceOf, text.toString());
      ct.deleteAndRestoreComments(variable);
      if (!(replaced instanceof PsiInstanceOfExpression instanceOfExpression)) {
        return;
      }
      PsiPrimaryPattern pattern = instanceOfExpression.getPattern();
      if (!(pattern instanceof PsiTypeTestPattern typeTestPattern)) {
        return;
      }
      PsiPatternVariable patternVariable = typeTestPattern.getPatternVariable();
      PsiElement scope = JavaSharedImplUtil.getPatternVariableDeclarationScope(instanceOfExpression);
      boolean nameDeclaredInside = InstanceOfUtils.isConflictingNameDeclaredInside(patternVariable, scope, true);
      if (!nameDeclaredInside) {
        return;
      }
      final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
      List<String> names = new VariableNameGenerator(instanceOfExpression, VariableKind.LOCAL_VARIABLE)
        .byType(patternVariable.getType()).byName(
          codeStyleManager.suggestUniqueVariableName(patternVariable.getName(), instanceOfExpression, true)).generateAll(true);
      updater.rename(patternVariable, names);
    }
  }
}
