// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiCapturedWildcardType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiDeconstructionPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiInstanceOfExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiPatternVariable;
import com.intellij.psi.PsiPrimaryPattern;
import com.intellij.psi.PsiRecordComponent;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.PsiWildcardType;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.light.LightRecordField;
import com.intellij.psi.impl.light.LightRecordMethod;
import com.intellij.psi.util.JavaPsiPatternUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.SideEffectChecker;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import com.siyeh.ig.psiutils.VariableNameGenerator;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

public final class DeconstructionCanBeUsedInspection extends AbstractBaseJavaLocalInspectionTool implements CleanupLocalInspectionTool {

  @Override
  public @NotNull Set<@NotNull JavaFeature> requiredFeatures() {
    return Set.of(JavaFeature.PATTERN_GUARDS_AND_RECORD_PATTERNS);
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitInstanceOfExpression(@NotNull PsiInstanceOfExpression expression) {
        PsiPrimaryPattern pattern = expression.getPattern();
        if (pattern instanceof PsiDeconstructionPattern) return;
        PsiPatternVariable variable = JavaPsiPatternUtil.getPatternVariable(pattern);
        if (variable == null) return;
        List<List<PsiReferenceExpression>> collect = collect(expression, variable, false);
        if (collect.isEmpty()) return;
        holder.registerProblem(variable.getTypeElement(), InspectionGadgetsBundle.message("inspection.deconstruction.can.be.used.message"),
                               new PatternVariableCanBeUsedFix());
      }
    };
  }

  private static List<List<PsiReferenceExpression>> collect(@NotNull PsiInstanceOfExpression instanceOf,
                                                            @NotNull PsiPatternVariable variable,
                                                            boolean shouldFindAll) {
    PsiType type = variable.getType();
    if (!(type instanceof PsiClassType classType)) return Collections.emptyList();
    PsiClass resolved = classType.resolve();
    if (resolved == null || !resolved.isRecord()) return Collections.emptyList();
    PsiRecordComponent[] components = resolved.getRecordComponents();
    if (components.length == 0) return Collections.emptyList();
    Set<PsiRecordComponent> used = new HashSet<>();
    List<PsiReferenceExpression> references = VariableAccessUtils.getVariableReferences(variable);
    List<List<PsiReferenceExpression>> result = new ArrayList<>();
    for (int i = 0; i < components.length; i++) {
      result.add(new ArrayList<>());
    }
    for (PsiReferenceExpression reference : references) {
      if (CastCanBeReplacedWithVariableInspection.isChangedBetween(variable, variable.getDeclarationScope(), instanceOf, reference)) {
        continue;
      }
      PsiRecordComponent component = getComponent(reference);
      if (component == null) return Collections.emptyList();
      if (!used.add(component) && !shouldFindAll) continue;
      PsiElementFactory factory = PsiElementFactory.getInstance(reference.getProject());
      PsiExpression call = factory.createExpressionFromText(reference.getText() + "." + component.getName() + "()", reference);
      if (SideEffectChecker.mayHaveSideEffects(call)) return Collections.emptyList();
      int index = ArrayUtil.indexOf(components, component);
      result.get(index).add((PsiReferenceExpression)PsiUtil.skipParenthesizedExprUp(reference.getParent()));
    }
    return used.size() == components.length ? result : Collections.emptyList();
  }

  private static @Nullable PsiRecordComponent getComponent(@NotNull PsiReferenceExpression reference) {
    PsiMethodCallExpression call = ExpressionUtils.getCallForQualifier(reference);
    if (call != null) {
      PsiMethod method = call.resolveMethod();
      if (method instanceof LightRecordMethod recordMethod) {
        return recordMethod.getRecordComponent();
      }
      else if (method != null) {
        PsiClass aClass = method.getContainingClass();
        if (aClass != null && aClass.findFieldByName(method.getName(), false) instanceof LightRecordField recordField) {
          return recordField.getRecordComponent();
        }
      }
    }
    else if (PsiUtil.skipParenthesizedExprUp(reference.getParent()) instanceof PsiReferenceExpression ref &&
             ref.resolve() instanceof LightRecordField recordField) {
      return recordField.getRecordComponent();
    }
    return null;
  }

  private static class PatternVariableCanBeUsedFix extends PsiUpdateModCommandQuickFix {
    @Override
    public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("inspection.deconstruction.can.be.used.fix.family.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiPatternVariable patternVariable = ObjectUtils.tryCast(element.getParent(), PsiPatternVariable.class);
      if (patternVariable == null) return;
      PsiInstanceOfExpression instanceOf = ObjectUtils.tryCast(patternVariable.getParent().getParent(), PsiInstanceOfExpression.class);
      if (instanceOf == null) return;
      List<List<PsiReferenceExpression>> collect = collect(instanceOf, patternVariable, true);
      StringJoiner deconstructionList = new StringJoiner(", ", "(", ")");
      List<String> usedNames = new ArrayList<>();
      for (List<PsiReferenceExpression> expressions : collect) {
        PsiReferenceExpression firstRef = expressions.getFirst();
        PsiType type = firstRef.getType();
        String deconstructionName = getDeconstructionName(expressions, usedNames, patternVariable);
        String stringType;
        if (type != null) {
          //example: if (obj instanceof Example<?> example)
          if (type instanceof PsiCapturedWildcardType || type instanceof PsiWildcardType) {
            stringType = "Object";
          }
          else {
            stringType = type.getCanonicalText();
          }
        }
        else {
          stringType = "var";
        }
        deconstructionList.add(stringType + " " + deconstructionName);
        for (PsiReferenceExpression expression : expressions) {
          PsiLocalVariable variable = getVariableFromInitializer(expression);
          if (variable != null) {
            var references = VariableAccessUtils.getVariableReferences(variable);
            for (PsiReferenceExpression ref : references) {
              ExpressionUtils.bindReferenceTo(ref, deconstructionName);
            }
            new CommentTracker().deleteAndRestoreComments(variable);
          }
          else {
            new CommentTracker().replace(expression.getParent() instanceof PsiMethodCallExpression call ? call : expression, deconstructionName);
          }
        }
      }

      PsiInstanceOfExpression replace =
        (PsiInstanceOfExpression)new CommentTracker().replace(instanceOf, instanceOf.getOperand().getText() +
                                                                          " instanceof " +
                                                                          element.getText() + deconstructionList + patternVariable.getName());
      PsiPrimaryPattern pattern = replace.getPattern();
      PsiPatternVariable variable = JavaPsiPatternUtil.getPatternVariable(pattern);
      assert variable != null;
      if (!VariableAccessUtils.variableIsUsed(variable, variable.getDeclarationScope())) {
        new CommentTracker().replace(replace, replace.getOperand().getText() +
                                              " instanceof " +
                                              element.getText() + deconstructionList);
      }
    }

    @NotNull
    private static String getDeconstructionName(@NotNull List<PsiReferenceExpression> expressions,
                                                @NotNull List<String> usedNames,
                                                @NotNull PsiPatternVariable patternVariable) {
      PsiReferenceExpression firstRef = expressions.getFirst();
      PsiVariable firstVariable = null;
      for (PsiReferenceExpression expression : expressions) {
        PsiLocalVariable variable = getVariableFromInitializer(expression);
        if (variable != null) {
          firstVariable = variable;
          break;
        }
      }
      String deconstructionName = StringUtil.substringAfter(firstRef.getText(), ".");
      if (firstVariable != null && firstVariable.getNameIdentifier() != null) {
        deconstructionName = firstVariable.getNameIdentifier().getText();
        usedNames.add(deconstructionName);
      }
      else {
        VariableNameGenerator generator = new VariableNameGenerator(patternVariable, VariableKind.PARAMETER)
          .skipNames(usedNames)
          .byName(deconstructionName);
        deconstructionName = generator.generate(true);
        usedNames.add(deconstructionName);
      }
      return deconstructionName;
    }

    private static @Nullable PsiLocalVariable getVariableFromInitializer(PsiReferenceExpression ref) {
      PsiElement parent = PsiUtil.skipParenthesizedExprUp(ref.getParent());
      if (parent instanceof PsiLocalVariable variable) {
        return variable;
      }
      if (parent instanceof PsiMethodCallExpression &&
          PsiUtil.skipParenthesizedExprUp(parent.getParent()) instanceof PsiLocalVariable variable) {
        return variable;
      }
      return null;
    }
  }
}