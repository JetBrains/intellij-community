// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.introduceField;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions;
import com.intellij.psi.PsiAnnotationMethod;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.NameSuggestionsGenerator;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.occurrences.ExpressionOccurrenceManager;
import com.intellij.refactoring.util.occurrences.OccurrenceFilter;
import com.intellij.refactoring.util.occurrences.OccurrenceManager;
import com.intellij.util.CommonJavaRefactoringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class IntroduceConstantHelper implements FieldHelper {
  @Override
  public boolean accept(@NotNull ElementToWorkOn elementToWorkOn) {
    final PsiExpression expr = elementToWorkOn.getExpression();
    if (expr != null) {
      return FieldExtractor.isStaticFinalInitializer(expr, true) == null;
    }
    final PsiLocalVariable localVariable = elementToWorkOn.getLocalVariable();
    if (localVariable == null) return false;
    final PsiExpression initializer = localVariable.getInitializer();
    boolean isValidInitializer = initializer != null && FieldExtractor.isStaticFinalInitializer(initializer, true) == null;
    return isValidInitializer;
  }

  @Override
  public boolean isConstant() {
    return true;
  }

  @Override
  public @NotNull String getRefactoringName() {
    return getRefactoringNameText();
  }

  public static @NlsActions.ActionText String getRefactoringNameText() {
    return RefactoringBundle.message("introduce.constant.title");
  }

  @Override
  public @Nullable String checkOccurrences(@NotNull PsiLocalVariable variable) {
    PsiExpression[] occurrences = CodeInsightUtil.findReferenceExpressions(CommonJavaRefactoringUtil.getVariableScope(variable), variable);
    for (PsiExpression occurrence : occurrences) {
      if (RefactoringUtil.isAssignmentLHS(occurrence)) {
        String message =
          RefactoringBundle.getCannotRefactorMessage(
            JavaRefactoringBundle.message("variable.is.accessed.for.writing", occurrence.getText()));
        return message;
      }
    }
    return null;
  }

  @Override
  public @Nullable @Nls String checkOccurrences(@NotNull PsiExpression expr, @NotNull PsiClass aClass) {
    OccurrenceManager manager = createOccurrenceManager(expr, aClass);
    PsiExpression[] occurrences = manager.getOccurrences();
    for (PsiExpression occurrence : occurrences) {
      if (RefactoringUtil.isAssignmentLHS(occurrence)) {
        String message =
          RefactoringBundle.getCannotRefactorMessage(
            JavaRefactoringBundle.message("variable.is.accessed.for.writing", occurrence.getText()));
        return message;
      }
    }
    return null;
  }

  @Override
  public @Nullable String checkClass(@NotNull PsiClass parentClass, @NotNull PsiExpression selectedExpr) {
    return null;
  }


  @Override
  public @NotNull OccurrenceManager createOccurrenceManager(final @NotNull PsiExpression selectedExpr,
                                                            final @NotNull PsiClass parentClass) {
    return new ExpressionOccurrenceManager(selectedExpr, parentClass, AnnotationEnumConstantFilter.INSTANCE);
  }

  @Override
  public @NotNull BaseExpressionToFieldHandler.Settings getSettings(@NotNull JavaIntroduceFieldService.ToFieldContext.VariableContext context,
                                                                    @NotNull JavaIntroduceFieldService.InitializationPlace place,
                                                                    @NotNull PsiExpression @NotNull [] occurrences) {
    PsiLocalVariable local = context.localVariable();
    PsiClass destinationClass = context.variableToFieldCandidatesContext().classes().getFirst();
    BaseExpressionToFieldHandler.Settings settings;
    boolean replaceAllOccurrences = true;
    Project project = local.getProject();
    boolean preselectNonNls = PropertiesComponent.getInstance(project).getBoolean(IntroduceConstantDialog.NONNLS_SELECTED_PROPERTY);
    PsiType defaultType = local.getType();
    defaultType = PsiTypesUtil.removeExternalAnnotations(defaultType);

    final String propertyName =
      JavaCodeStyleManager.getInstance(project).variableNameToPropertyName(local.getName(), VariableKind.LOCAL_VARIABLE);

    PsiExpression expr = local.getInitializer();
    NameSuggestionsGenerator generator =
      IntroduceConstantDialog.createNameSuggestionGenerator(propertyName, expr, JavaCodeStyleManager.getInstance(project), null,
                                                            destinationClass);
    SuggestedNameInfo suggestedNameInfo = generator.getSuggestedNameInfo(defaultType);

    String visibility = getVisibility();
    //noinspection MagicConstant
    settings = new BaseExpressionToFieldHandler.Settings(suggestedNameInfo.names[0], expr, occurrences,
                                                         replaceAllOccurrences, true, true,
                                                         place,
                                                         visibility, local, defaultType, true, destinationClass,
                                                         preselectNonNls, false);
    return settings;
  }

  @Override
  public @NotNull String getVisibility() {
    return PsiModifier.PUBLIC;
  }

  @Override
  public @NotNull SuggestedNameInfo getSuggestedNameInfo(@NotNull JavaIntroduceFieldService.ToFieldContext.ExpressionContext expressionContextContext,
                                                         @NotNull FieldExtractor.SettingParameters parameters) {
    JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(expressionContextContext.selectedExpr().getProject());
    NameSuggestionsGenerator generator =
      IntroduceConstantDialog.createNameSuggestionGenerator(null, expressionContextContext.selectedExpr(), codeStyleManager, null,
                                                            expressionContextContext.parentClass());
    return generator.getSuggestedNameInfo(expressionContextContext.tempType());
  }

  @Override
  public @Nullable InvalidInitializer checkInitializer(@Nullable PsiExpression expr,
                                                       @Nullable PsiLocalVariable localVariable) {
    if (localVariable == null) {
      if (expr == null) return null;
      if (isAnnotationEnumAttributeValue(expr)) {
        String message = RefactoringBundle.getCannotRefactorMessage(
          JavaRefactoringBundle.message("constant.not.allowed.as.annotation.enum.value"));
        return new InvalidInitializer(message, expr);
      }
      PsiElement errorElement = FieldExtractor.isStaticFinalInitializer(expr, true);
      if (errorElement != null) {
        String message = RefactoringBundle.getCannotRefactorMessage(
          JavaRefactoringBundle.message("selected.expression.cannot.be.a.constant.initializer"));
        return new InvalidInitializer(message, errorElement);
      }
      return null;
    }
    PsiExpression initializer = localVariable.getInitializer();
    if (initializer == null) {
      String message = RefactoringBundle.getCannotRefactorMessage(
        JavaRefactoringBundle.message("variable.does.not.have.an.initializer", localVariable.getName()));
      return new InvalidInitializer(message, null);
    }
    PsiElement errorElement = FieldExtractor.isStaticFinalInitializer(initializer, true);
    if (errorElement != null) {
      String message = RefactoringBundle.getCannotRefactorMessage(
        JavaRefactoringBundle.message("initializer.for.variable.cannot.be.a.constant.initializer", localVariable.getName()));
      return new InvalidInitializer(message, errorElement);
    }
    return null;
  }

  private static boolean isAnnotationEnumAttributeValue(@NotNull PsiExpression expression) {
    PsiType type = expression.getType();
    PsiClass valueClass = PsiUtil.resolveClassInClassTypeOnly(type);
    if (valueClass == null || !valueClass.isEnum()) return false;

    PsiElement element = BaseExpressionToFieldHandler.getPhysicalElement(expression);
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(element.getParent());
    return parent instanceof PsiNameValuePair || parent instanceof PsiAnnotationMethod || parent instanceof PsiArrayInitializerMemberValue;
  }

  private static class AnnotationEnumConstantFilter implements OccurrenceFilter {
    private static final AnnotationEnumConstantFilter INSTANCE = new AnnotationEnumConstantFilter();

    @Override
    public boolean isOK(@NotNull PsiExpression occurrence) {
      return !isAnnotationEnumAttributeValue(occurrence);
    }
  }
}
