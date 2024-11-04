// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.light.LightRecordMethod;
import com.intellij.psi.impl.source.tree.JavaSharedImplUtil;
import com.intellij.psi.util.InheritanceUtil;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.intellij.codeInspection.options.OptPane.checkbox;

public final class PatternVariableCanBeUsedInspection extends AbstractBaseJavaLocalInspectionTool implements CleanupLocalInspectionTool {

  @SuppressWarnings("PublicField")
  public boolean reportAlsoCastWithIntroducingNewVariable = false;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return OptPane.pane(checkbox("reportAlsoCastWithIntroducingNewVariable",
                                 InspectionGadgetsBundle.message("inspection.pattern.variable.can.be.used.report.cast.only")));
  }

  @Override
  public @NotNull Set<@NotNull JavaFeature> requiredFeatures() {
    return Set.of(JavaFeature.PATTERNS);
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
        if (!PsiUtil.isAvailable(JavaFeature.PATTERN_GUARDS_AND_RECORD_PATTERNS, holder.getFile())) return;
        PsiTypeCastExpression qualifier = getQualifierReferenceExpression(call);
        if (qualifier == null) return;
        PsiInstanceOfExpression candidate = InstanceOfUtils.findPatternCandidate(qualifier);
        PsiTypeElement castTypeElement = qualifier.getCastType();
        if (castTypeElement == null || candidate == null) return;
        if (!compatibleTypes(candidate, castTypeElement.getType())) return;
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
      public void visitTypeCastExpression(@NotNull PsiTypeCastExpression expression) {
        InstanceOfCandidateResult result = findInstanceOfCandidateResult(expression);
        if (result == null) return;
        if (result.instanceOf() != null) {
          PsiPattern pattern = result.instanceOf().getPattern();
          PsiPatternVariable existingPatternVariable = JavaPsiPatternUtil.getPatternVariable(pattern);
          if (pattern != null && existingPatternVariable == null) {
            return;
          }
          if (existingPatternVariable != null) {
            if (!isFinalOrEffectivelyFinal(existingPatternVariable)) {
              return;
            }
            holder.registerProblem(result.castTypeElement(),
                                   InspectionGadgetsBundle.message("inspection.pattern.variable.can.be.used.existing.cast.message",
                                                                   existingPatternVariable.getName()),
                                   new ExistingPatternVariableCanBeUsedFix(null, existingPatternVariable));
          }
          else {
            if (!reportAlsoCastWithIntroducingNewVariable) {
              if (isOnTheFly) {
                holder.registerProblem(expression,
                                       InspectionGadgetsBundle.message("inspection.pattern.variable.can.be.used.instead.of.cast.message"),
                                       ProblemHighlightType.INFORMATION,
                                       new CastExpressionsCanBeReplacedWithPatternVariableFix(result.instanceOf()));
              }
              return;
            }
            holder.registerProblem(result.castTypeElement(),
                                   InspectionGadgetsBundle.message("inspection.pattern.variable.can.be.used.instead.of.cast.message"),
                                   new CastExpressionsCanBeReplacedWithPatternVariableFix(result.instanceOf()));
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
        if (castType instanceof PsiPrimitiveType &&
            !PsiUtil.isAvailable(JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS, operand)) {
          return;
        }
        if (!variable.getType().equals(castType)) return;
        PsiType operandType = operand.getType();
        if (operandType == null || castType.isAssignableFrom(operandType)) return;
        PsiElement scope = PsiUtil.getVariableCodeBlock(variable, null);
        if (scope == null) return;
        PsiDeclarationStatement declaration = ObjectUtils.tryCast(variable.getParent(), PsiDeclarationStatement.class);
        if (declaration == null) return;
        PsiInstanceOfExpression instanceOf = InstanceOfUtils.findPatternCandidate(cast, variable);
        if (instanceOf == null) return;
        if (!compatibleTypes(instanceOf, castType)) return;
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
        }
        else {
          if (!isOnTheFly && InstanceOfUtils.hasConflictingDeclaredNames(variable, instanceOf)) {
            return;
          }
          holder.registerProblem(identifier,
                                 InspectionGadgetsBundle.message("inspection.pattern.variable.can.be.used.message", name),
                                 new PatternVariableCanBeUsedFix(name, instanceOf));
        }
      }
    };
  }

  private static boolean compatibleTypes(@NotNull PsiInstanceOfExpression instanceOfExpression, @NotNull PsiType castType) {
    PsiTypeElement typeElement = instanceOfExpression.getCheckType();
    if (typeElement == null) {
      if (instanceOfExpression.getPattern() instanceof PsiTypeTestPattern typeTestPattern) {
        typeElement = typeTestPattern.getCheckType();
      }
      if (instanceOfExpression.getPattern() instanceof PsiDeconstructionPattern deconstructionPattern) {
        return deconstructionPattern.getTypeElement().getType().equals(castType);
      }
    }
    if (typeElement == null) return false;
    PsiType instanceOfType = typeElement.getType();
    if (instanceOfType instanceof PsiClassType instanceOfClassType && !instanceOfClassType.isRaw() &&
        castType instanceof PsiClassType castClassType && castClassType.isRaw()) {
      return false;
    }
    return castType.isAssignableFrom(instanceOfType);
  }

  private static class ExistingPatternVariableCanBeUsedFix extends PsiUpdateModCommandQuickFix {
    private final @Nullable String myName;
    private final @NotNull String myPatternName;

    /**
     * Creates a fix for using an existing pattern variable.
     *
     * @param name             The name of the variable being replaced (null if there is no such a variable, only cast expressions)
     * @param existingVariable The existing pattern variable to use
     */
    private ExistingPatternVariableCanBeUsedFix(@Nullable String name, @NotNull PsiPatternVariable existingVariable) {
      myName = name;
      myPatternName = existingVariable.getName();
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getName() {
      if (myName != null) {
        return InspectionGadgetsBundle.message("inspection.pattern.variable.can.be.used.existing.fix.name", myName, myPatternName);
      }
      return InspectionGadgetsBundle.message("inspection.pattern.variable.instead.of.cast.can.be.used.existing.fix.name", myPatternName);
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("inspection.pattern.variable.can.be.used.existing.fix.family.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (myName == null) {
        PsiElement castExpression = PsiTreeUtil.getParentOfType(element, PsiTypeCastExpression.class);
        if (castExpression == null) return;
        while (castExpression.getParent() instanceof PsiParenthesizedExpression parenthesizedExpression) {
          castExpression = parenthesizedExpression;
        }
        new CommentTracker().replace(castExpression, myPatternName);
      }
      else if (!myName.endsWith("()")) {
        PsiLocalVariable variable = PsiTreeUtil.getParentOfType(element, PsiLocalVariable.class);
        if (variable == null) return;
        if (VariableAccessUtils.variableIsAssigned(variable)) {
          new CommentTracker().replace(element, myPatternName);
          return;
        }
        List<PsiReferenceExpression> references = VariableAccessUtils.getVariableReferences(variable);
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

  private static class CastExpressionsCanBeReplacedWithPatternVariableFix extends PsiUpdateModCommandQuickFix {

    @NotNull
    private final SmartPsiElementPointer<PsiInstanceOfExpression> myInstanceOfPointer;

    private CastExpressionsCanBeReplacedWithPatternVariableFix(@NotNull PsiInstanceOfExpression instanceOf) {
      myInstanceOfPointer = SmartPointerManager.createPointer(instanceOf);
    }

    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("inspection.pattern.variable.instead.of.cast.can.be.used.fix.family.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiTypeCastExpression castExpression = PsiTreeUtil.getParentOfType(element, false, PsiTypeCastExpression.class);
      if (castExpression == null) return;
      PsiTypeElement originalTypeElement = castExpression.getCastType();
      if (originalTypeElement == null) return;
      PsiInstanceOfExpression originalInstanceOf = myInstanceOfPointer.getElement();
      if (originalInstanceOf == null) return;
      PsiInstanceOfExpression instanceOf = PsiTreeUtil.findSameElementInCopy(originalInstanceOf, element.getContainingFile());
      if (instanceOf.getPattern() instanceof PsiDeconstructionPattern) return;
      PsiTypeElement instanceOfType = instanceOf.getCheckType();
      PsiTypeElement typeElement = getTypeElement(originalTypeElement, instanceOfType);
      if (typeElement == null) return;
      PsiStatement psiIfStatement = PsiTreeUtil.getParentOfType(instanceOf, PsiStatement.class);
      if (psiIfStatement == null || psiIfStatement.getParent() == null) return;
      var visitor = new JavaRecursiveElementVisitor() {
        final List<PsiTypeCastExpression> myCasts = new ArrayList<>();

        @Override
        public void visitTypeCastExpression(@NotNull PsiTypeCastExpression expression) {
          PsiTypeElement castType = expression.getCastType();
          if (castType == null) return;
          if (!castType.textMatches(originalTypeElement)) {
            return;
          }
          InstanceOfCandidateResult result = findInstanceOfCandidateResult(expression);
          if (result != null && result.instanceOf == instanceOf) {
            myCasts.add(expression);
          }
        }
      };

      psiIfStatement.getParent().accept(visitor);
      List<PsiTypeCastExpression> casts = visitor.myCasts;
      if (casts.isEmpty()) return;

      CommentTracker ct = new CommentTracker();
      StringBuilder text = generateTextForInstanceOf(null, ct, instanceOf, typeElement);
      if (text == null) return;
      PsiElement replaced = ct.replace(instanceOf, text.toString());
      if (!(replaced instanceof PsiInstanceOfExpression instanceOfExpression)) {
        return;
      }
      PsiPrimaryPattern pattern = instanceOfExpression.getPattern();
      if (!(pattern instanceof PsiTypeTestPattern typeTestPattern)) {
        return;
      }
      PsiPatternVariable patternVariable = typeTestPattern.getPatternVariable();
      if (patternVariable == null) return;
      String variableName = patternVariable.getName();
      for (PsiTypeCastExpression cast : casts) {
        PsiElement currentCast = cast;
        CommentTracker castCt = new CommentTracker();
        while (currentCast.getParent() instanceof PsiParenthesizedExpression parenthesizedExpression) {
          currentCast = parenthesizedExpression;
        }
        castCt.replace(currentCast, variableName);
      }
      final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
      List<String> names = new VariableNameGenerator(instanceOfExpression, VariableKind.LOCAL_VARIABLE)
        .byType(patternVariable.getType()).byName(
          codeStyleManager.suggestUniqueVariableName(variableName, instanceOfExpression, true)).generateAll(true);
      names.remove(patternVariable.getName());
      updater.rename(patternVariable, names);
    }
  }

  private static PsiTypeElement getTypeElement(PsiTypeElement originalTypeElement, PsiTypeElement instanceOfType) {
    PsiTypeElement typeElement = instanceOfType;
    if (instanceOfType != null && instanceOfType.getType() instanceof PsiClassType instanceOfClassType && instanceOfClassType.isRaw()) {
      if (originalTypeElement.getType() instanceof PsiClassType originalClassType && !originalClassType.isRaw()) {
        PsiClass instanceOfClass = PsiUtil.resolveClassInClassTypeOnly(instanceOfClassType);
        PsiClass originalClass = PsiUtil.resolveClassInClassTypeOnly(originalClassType);
        if (originalClass != null && originalClass.getQualifiedName() != null) {
          if (InheritanceUtil.isInheritor(instanceOfClass, false, originalClass.getQualifiedName())) {
            PsiClassType genericType = GenericsUtil.getExpectedGenericType(instanceOfClass, instanceOfClass, originalClassType);
            typeElement = JavaPsiFacade.getElementFactory(instanceOfClass.getProject()).createTypeElement(genericType);
          }
        }
      }
    }
    return typeElement;
  }

  private static class PatternVariableCanBeUsedFix extends PsiUpdateModCommandQuickFix {
    @NotNull
    private final SmartPsiElementPointer<PsiInstanceOfExpression> myInstanceOfPointer;
    @NotNull
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
      if (!(element.getParent() instanceof PsiLocalVariable variable)) return;
      PsiTypeCastExpression cast = ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(variable.getInitializer()),
                                                       PsiTypeCastExpression.class);
      if (cast == null) return;
      PsiInstanceOfExpression instanceOf = PsiTreeUtil.findSameElementInCopy(myInstanceOfPointer.getElement(), element.getContainingFile());
      if (instanceOf == null) return;
      PsiTypeElement typeElement = getTypeElement(cast.getCastType(), instanceOf.getCheckType());
      if (typeElement == null) return;
      CommentTracker ct = new CommentTracker();
      StringBuilder text = generateTextForInstanceOf(variable, ct, instanceOf, typeElement);
      if (text == null) return;
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

  private static @Nullable InstanceOfCandidateResult findInstanceOfCandidateResult(@NotNull PsiTypeCastExpression expression) {
    PsiTypeElement castTypeElement = expression.getCastType();
    if (castTypeElement == null) return null;
    PsiExpression operand = expression.getOperand();
    if (!(operand instanceof PsiReferenceExpression)) return null;
    PsiType castType = castTypeElement.getType();
    if (castType instanceof PsiPrimitiveType &&
        !PsiUtil.isAvailable(JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS, operand)) {
      return null;
    }
    PsiType operandType = operand.getType();
    if (operandType == null || castType.isAssignableFrom(operandType)) return null;
    PsiInstanceOfExpression instanceOf = InstanceOfUtils.findPatternCandidate(expression, null);
    if (instanceOf == null) return null;
    if (!compatibleTypes(instanceOf, castType)) return null;
    return new InstanceOfCandidateResult(castTypeElement, instanceOf);
  }

  private record InstanceOfCandidateResult(@NotNull PsiTypeElement castTypeElement,
                                           @Nullable PsiInstanceOfExpression instanceOf) {
  }

  private static @Nullable StringBuilder generateTextForInstanceOf(@Nullable PsiLocalVariable variable,
                                                                   @NotNull CommentTracker ct,
                                                                   @NotNull PsiInstanceOfExpression instanceOf,
                                                                   @NotNull PsiTypeElement typeElement) {
    StringBuilder text = new StringBuilder(ct.text(instanceOf.getOperand()));
    text.append(" instanceof ");
    PsiModifierList modifierList = variable != null ? variable.getModifierList() : null;
    JavaCodeStyleSettings codeStyleSettings = JavaCodeStyleSettings.getInstance(instanceOf.getContainingFile());
    if (modifierList != null && modifierList.getTextLength() > 0) {
      modifierList.setModifierProperty(PsiModifier.FINAL, codeStyleSettings.GENERATE_FINAL_LOCALS);
      text.append(ct.text(modifierList)).append(' ');
    }
    else if (codeStyleSettings.GENERATE_FINAL_LOCALS) {
      text.append("final ");
    }
    text.append(typeElement.getText()).append(' ');
    if (instanceOf.getPattern() instanceof PsiDeconstructionPattern) {
      return null;
    }
    if (variable == null) {
      String name = new VariableNameGenerator(instanceOf, VariableKind.LOCAL_VARIABLE)
        .byType(typeElement.getType()).generate(true);
      text.append(name);
    }
    else {
      text.append(variable.getName());
    }
    return text;
  }
}
