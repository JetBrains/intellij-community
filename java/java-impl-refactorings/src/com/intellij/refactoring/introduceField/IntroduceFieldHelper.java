// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.introduceField;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLambdaExpressionType;
import com.intellij.psi.PsiLambdaParameterType;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethodReferenceType;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.JavaNameSuggestionUtil;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.refactoring.util.occurrences.ExpressionOccurrenceManager;
import com.intellij.refactoring.util.occurrences.NotInConstructorCallFilter;
import com.intellij.refactoring.util.occurrences.OccurrenceFilter;
import com.intellij.refactoring.util.occurrences.OccurrenceManager;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class IntroduceFieldHelper implements FieldHelper {
  @Override
  public boolean accept(@NotNull ElementToWorkOn on) {
    return true;
  }

  @Override
  public boolean isConstant() {
    return false;
  }

  @Override
  public @NotNull String getRefactoringName() {
    return getRefactoringNameText();
  }

  @Override
  @NlsSafe
  public @Nullable String checkOccurrences(@NotNull PsiLocalVariable variable) {
    return null;
  }

  @Override
  public @Nullable @Nls String checkOccurrences(@NotNull PsiExpression expr, @NotNull PsiClass aClass) {
    return null;
  }

  public static @NlsContexts.DialogTitle String getRefactoringNameText() {
    return RefactoringBundle.message("introduce.field.title");
  }


  @Override
  @Nullable
  public String checkClass(@NotNull PsiClass parentClass, @NotNull PsiExpression selectedExpr) {
    return checkCanIntroduceField(parentClass, selectedExpr.getType());
  }

  static @Nullable @NlsContexts.DialogMessage String checkCanIntroduceField(@NotNull PsiClass parentClass, @Nullable PsiType type) {
    if (parentClass.isInterface()) {
      return JavaRefactoringBundle.message("cannot.introduce.field.in.interface");
    }
    if (PsiTypes.nullType().equals(type) || type instanceof PsiLambdaParameterType || type instanceof PsiLambdaExpressionType ||
        type instanceof PsiMethodReferenceType) {
      return JavaRefactoringBundle.message("variable.type.unknown");
    }
    PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(type);
    if (aClass != null && PsiUtil.isLocalClass(aClass) && !PsiTreeUtil.isAncestor(aClass, parentClass, false)) {
      String message = JavaRefactoringBundle.message("0.is.not.visible.to.members.of.1",
                                                     RefactoringUIUtil.getDescription(aClass, false),
                                                     RefactoringUIUtil.getDescription(parentClass, false));
      return StringUtil.capitalize(message);
    }
    return null;
  }

  @Override
  public @NotNull OccurrenceManager createOccurrenceManager(final @NotNull PsiExpression selectedExpr,
                                                            final @NotNull PsiClass parentClass) {
    final OccurrenceFilter occurrenceFilter = FieldExtractor.isInSuperOrThis(selectedExpr) ? null : NotInConstructorCallFilter.INSTANCE;
    return new ExpressionOccurrenceManager(selectedExpr, parentClass, occurrenceFilter, true);
  }

  @Override
  public @NotNull BaseExpressionToFieldHandler.Settings getSettings(@NotNull JavaIntroduceFieldService.ToFieldContext.VariableContext context,
                                                                    @NotNull JavaIntroduceFieldService.InitializationPlace place,
                                                                    @NotNull PsiExpression @NotNull [] occurrences) {
    PsiLocalVariable local = context.localVariable();
    PsiExpression expr = local.getInitializer();
    PsiType defaultType = local.getType();
    PsiClass destinationClass = context.variableToFieldCandidatesContext().classes().getFirst();

    @PsiModifier.ModifierConstant String visibility = JavaRefactoringSettings.getInstance().INTRODUCE_FIELD_VISIBILITY;
    if (visibility == null) {
      visibility = PsiModifier.PRIVATE;
    }
    final PsiStatement statement = PsiTreeUtil.getParentOfType(local, PsiStatement.class);
    FieldExtractor.SettingParameters parameters =
      FieldExtractor.getParameters(destinationClass, expr, occurrences, local, statement, false);

    return new BaseExpressionToFieldHandler.Settings(local.getName(), expr, occurrences,
                                                     false, parameters.declareStatic(), true,
                                                     place,
                                                     visibility, local, defaultType, true, destinationClass,
                                                     false, false);
  }

  @Override
  public @NotNull String getVisibility() {
    String visibility = JavaRefactoringSettings.getInstance().INTRODUCE_FIELD_VISIBILITY;
    if (visibility == null) {
      visibility = PsiModifier.PRIVATE;
    }
    return visibility;
  }

  @Override
  public @NotNull SuggestedNameInfo getSuggestedNameInfo(@NotNull JavaIntroduceFieldService.ToFieldContext.ExpressionContext expressionContextContext,
                                                         FieldExtractor.@NotNull SettingParameters parameters) {
    return JavaNameSuggestionUtil.suggestFieldName(expressionContextContext.tempType(), null, expressionContextContext.selectedExpr(),
                                            parameters.declareStatic(), expressionContextContext.parentClass());

  }
}
