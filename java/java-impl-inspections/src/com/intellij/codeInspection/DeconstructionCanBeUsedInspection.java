// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.light.LightRecordField;
import com.intellij.psi.impl.light.LightRecordMethod;
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

import java.util.*;

public class DeconstructionCanBeUsedInspection extends AbstractBaseJavaLocalInspectionTool {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!HighlightingFeature.PATTERN_GUARDS_AND_RECORD_PATTERNS.isAvailable(holder.getFile())) return PsiElementVisitor.EMPTY_VISITOR;
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
                               new PatternVariableCanBeUsedFix(expression));
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
    List<PsiReferenceExpression> references =
      VariableAccessUtils.getVariableReferences(variable, variable.getDeclarationScope());
    List<List<PsiReferenceExpression>> result = new ArrayList<>();
    for (int i = 0; i < components.length; i++) {
      result.add(new ArrayList<>());
    }
    for (PsiReferenceExpression reference : references) {
      if (CastCanBeReplacedWithVariableInspection.isChangedBetween(variable, variable.getDeclarationScope(), instanceOf, reference)) {
        continue;
      }
      PsiRecordComponent component = getComponent(reference);
      if (component == null) continue;
      if (!used.add(component) && !shouldFindAll) continue;
      PsiElementFactory factory = PsiElementFactory.getInstance(reference.getProject());
      PsiExpression call = factory.createExpressionFromText(reference.getText() + "." + component.getName() + "()", reference);
      if (SideEffectChecker.mayHaveSideEffects(call)) return Collections.emptyList();
      int index = ArrayUtil.indexOf(components, component);
      result.get(index).add((PsiReferenceExpression)PsiUtil.skipParenthesizedExprUp(reference.getParent()));
      if (!shouldFindAll && used.size() == components.length) {
        return result;
      }
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

  private static class PatternVariableCanBeUsedFix implements LocalQuickFix {
    private final SmartPsiElementPointer<PsiInstanceOfExpression> myInstanceOfPointer;

    private PatternVariableCanBeUsedFix(@NotNull PsiInstanceOfExpression instanceOf) {
      myInstanceOfPointer = SmartPointerManager.createPointer(instanceOf);
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("inspection.deconstruction.can.be.used.fix.family.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiPatternVariable patternVariable = ObjectUtils.tryCast(descriptor.getStartElement().getParent(), PsiPatternVariable.class);
      if (patternVariable == null) return;
      PsiInstanceOfExpression instanceOf = ObjectUtils.tryCast(patternVariable.getParent().getParent(), PsiInstanceOfExpression.class);
      if (instanceOf == null) return;
      List<List<PsiReferenceExpression>> collect = collect(instanceOf, patternVariable, true);
      StringJoiner deconstructionList = new StringJoiner(", ", "(", ")");
      for (List<PsiReferenceExpression> expressions : collect) {
        PsiReferenceExpression firstRef = expressions.get(0);
        PsiType type = firstRef.getType();
        String s = StringUtil.substringAfter(firstRef.getText(), ".");
        VariableNameGenerator generator = new VariableNameGenerator(patternVariable, VariableKind.PARAMETER).byName(s);
        s = generator.generate(false);
        deconstructionList.add((type != null ? type.getCanonicalText() : "var") + " " + s);
        for (PsiReferenceExpression expression : expressions) {
          PsiLocalVariable variable = foo(expression);
          if (variable != null) {
            var references = VariableAccessUtils.getVariableReferences(variable, PsiUtil.getVariableCodeBlock(variable, null));
            for (PsiReferenceExpression ref : references) {
              ExpressionUtils.bindReferenceTo(ref, s);
            }
            new CommentTracker().deleteAndRestoreComments(variable);
          }
          else {
            new CommentTracker().replace(expression.getParent() instanceof PsiMethodCallExpression call ? call : expression, s);
          }
        }
      }

      PsiInstanceOfExpression replace =
        (PsiInstanceOfExpression)new CommentTracker().replace(instanceOf, instanceOf.getOperand().getText() +
                                                                          " instanceof " +
                                                                          descriptor.getPsiElement().getText() +
                                                                          deconstructionList +
                                                                          ((PsiVariable)descriptor.getStartElement()
                                                                            .getParent()).getName());
      PsiPrimaryPattern pattern = replace.getPattern();
      PsiPatternVariable variable = JavaPsiPatternUtil.getPatternVariable(pattern);
      assert variable != null;
      if (!VariableAccessUtils.variableIsUsed(variable, variable.getDeclarationScope())) {
        new CommentTracker().replace(replace, replace.getOperand().getText() +
                                              " instanceof " +
                                              descriptor.getPsiElement().getText() +
                                              deconstructionList);
      }
    }

    @Override
    public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {

      PsiInstanceOfExpression instanceOf = myInstanceOfPointer.getElement();
      return instanceOf == null ? null : new PatternVariableCanBeUsedFix(PsiTreeUtil.findSameElementInCopy(instanceOf, target));
    }

    private static @Nullable PsiLocalVariable foo(PsiReferenceExpression ref) {
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