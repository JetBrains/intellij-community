// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.introduceField;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.LambdaUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiParserFacade;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.util.FileTypeUtils;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.JavaNameSuggestionUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.occurrences.NotInConstructorCallFilter;
import com.intellij.refactoring.util.occurrences.OccurrenceManager;
import com.intellij.util.CommonJavaRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.intellij.refactoring.introduceField.IntroduceFieldCentralPanel.getInitializationPlaceParameters;

final class FieldExtractor {
  private static final Logger LOG = Logger.getInstance(FieldExtractor.class);

  @NotNull
  private final BaseExpressionToFieldHandler myHandler;

  FieldExtractor(@NotNull BaseExpressionToFieldHandler handler) {
    myHandler = handler;
  }

  static JavaIntroduceFieldService.ExpressionToFieldContext getContext(@NotNull BaseExpressionToFieldHandler handler,
                                                                       @NotNull PsiExpression selectedExpr) {
    if (!Comparing.strEqual(IntroduceConstantHandler.getRefactoringNameText(), handler.getRefactoringName()) &&
        isInSuperOrThis(selectedExpr) &&
        handler.isStaticFinalInitializer(selectedExpr) != null) {
      return new JavaIntroduceFieldService.ExpressionToFieldContext.Error(
        RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("invalid.expression.context")));
    }

    final PsiElement element = BaseExpressionToFieldHandler.getPhysicalElement(selectedExpr);

    final PsiFile file = element.getContainingFile();
    LOG.assertTrue(file != null, "expr.getContainingFile() == null");

    if (LOG.isDebugEnabled()) {
      LOG.debug("expression:" + selectedExpr);
    }

    final PsiType tempType = getTypeByExpression(selectedExpr);
    if (tempType == null || LambdaUtil.notInferredType(tempType)) {
      String message = RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("unknown.expression.type"));
      return new JavaIntroduceFieldService.ExpressionToFieldContext.Error(message);
    }

    if (PsiTypes.voidType().equals(tempType)) {
      String message = RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("selected.expression.has.void.type"));
      return new JavaIntroduceFieldService.ExpressionToFieldContext.Error(message);
    }

    String switchLabelError = RefactoringUtil.checkEnumConstantInSwitchLabel(selectedExpr);
    if (switchLabelError != null) {
      String message = RefactoringBundle.getCannotRefactorMessage(switchLabelError);
      return new JavaIntroduceFieldService.ExpressionToFieldContext.Error(message);
    }
    PsiClass parentClass = handler.getParentClass(selectedExpr);
    String message = checkParentClass(parentClass, file, handler);
    if (message != null) {
      return new JavaIntroduceFieldService.ExpressionToFieldContext.Error(message);
    }
    if (parentClass == null) {
      return new JavaIntroduceFieldService.ExpressionToFieldContext.Error(
        JavaRefactoringBundle.message("selected.expression.cannot.be.extracted"));
    }
    final List<PsiClass> proposedClasses = new ArrayList<>();
    PsiClass aClass = parentClass;
    while (aClass != null) {
      proposedClasses.add(aClass);
      final PsiField psiField = BaseExpressionToFieldHandler.ConvertToFieldRunnable.checkForwardRefs(selectedExpr, aClass);
      if (psiField != null && psiField.getParent() == aClass) break;
      aClass = PsiTreeUtil.getParentOfType(aClass, PsiClass.class, true);
    }
    proposedClasses.removeIf(proposedClass -> proposedClass == null ||
                                              handler.checkClass(proposedClass, selectedExpr) != null);
    if (proposedClasses.isEmpty()) {
      message = handler.checkClass(parentClass, selectedExpr);
      if (message != null) {
        return new JavaIntroduceFieldService.ExpressionToFieldContext.Error(RefactoringBundle.getCannotRefactorMessage(message));
      }
      return new JavaIntroduceFieldService.ExpressionToFieldContext.Error(
        JavaRefactoringBundle.message("selected.expression.cannot.be.extracted"));
    }
    return new JavaIntroduceFieldService.ExpressionToFieldContext.Success(selectedExpr, element, file, tempType, parentClass,
                                                                          proposedClasses);
  }


  @NotNull JavaIntroduceFieldService.ExpressionToFieldContext getContext(@NotNull PsiExpression selectedExpr) {
    return getContext(myHandler, selectedExpr);
  }

  @NotNull JavaIntroduceFieldService.AvailableSettings getAvailableSettings(@NotNull JavaIntroduceFieldService.ExpressionToFieldContext.Success context) {
    ArrayList<JavaIntroduceFieldService.InitializationPlace> places =
      new ArrayList<>(List.of(JavaIntroduceFieldService.InitializationPlace.values()));
    final OccurrenceManager occurrenceManager = myHandler.createOccurrenceManager(context.selectedExpr(), context.parentClass());
    final PsiExpression[] occurrences = occurrenceManager.getOccurrences();
    final PsiElement anchorStatementIfAll = occurrenceManager.getAnchorStatementForAll();
    PsiElement tempAnchorElement = CommonJavaRefactoringUtil.getParentExpressionAnchorElement(context.selectedExpr());
    SettingParameters parameters = getParameters(context.parentClass(), context.selectedExpr(), occurrences,
                                                 tempAnchorElement == null
                                                 ? context.selectedExpr()
                                                 : tempAnchorElement,
                                                 anchorStatementIfAll);

    Project project = context.parentClass().getProject();
    boolean isTestClass = !DumbService.isDumb(project) && TestFrameworks.getInstance().isTestClass(context.parentClass());
    IntroduceFieldCentralPanel.InitializationParameters initializationPlaceParameters =
      getInitializationPlaceParameters(context.selectedExpr(), isTestClass);

    if (initializationPlaceParameters != null) {
      if (initializationPlaceParameters.locals()) {
        places.remove(JavaIntroduceFieldService.InitializationPlace.IN_FIELD_DECLARATION);
      }
      if (!initializationPlaceParameters.constructor()) {
        places.remove(JavaIntroduceFieldService.InitializationPlace.IN_CONSTRUCTOR);
      }
      if (!initializationPlaceParameters.insetup()) {
        places.remove(JavaIntroduceFieldService.InitializationPlace.IN_SETUP_METHOD);
      }
    }

    if (!isTestClass) {
      places.remove(JavaIntroduceFieldService.InitializationPlace.IN_SETUP_METHOD);
    }

    if (!parameters.allowInitInMethod()) {
      places.remove(JavaIntroduceFieldService.InitializationPlace.IN_CURRENT_METHOD);
    }
    boolean inOnlyConstructor = parameters.currentMethodConstructor() && context.parentClass().getConstructors().length == 1;
    if (parameters.declareStatic() || inOnlyConstructor) {
      places.remove(JavaIntroduceFieldService.InitializationPlace.IN_CONSTRUCTOR);
    }

    return new JavaIntroduceFieldService.AvailableSettings(places);
  }

  @Nullable PsiField extractField(@NotNull PsiExpression expression, @NotNull JavaIntroduceFieldService.InitializationPlace place) {
    JavaIntroduceFieldService.ExpressionToFieldContext context = getContext(expression);
    if (!(context instanceof JavaIntroduceFieldService.ExpressionToFieldContext.Success successContext)) {
      return null;
    }
    final OccurrenceManager occurrenceManager =
      myHandler.createOccurrenceManager(successContext.selectedExpr(), successContext.parentClass());
    final PsiExpression[] occurrences = occurrenceManager.getOccurrences();
    final PsiElement anchorStatementIfAll = occurrenceManager.getAnchorStatementForAll();

    PsiElement tempAnchorElement = CommonJavaRefactoringUtil.getParentExpressionAnchorElement(successContext.selectedExpr());
    if (tempAnchorElement == null) {
      tempAnchorElement = successContext.selectedExpr();
    }
    SettingParameters parameters = getParameters(successContext.parentClass(), successContext.selectedExpr(),
                                                 occurrences, tempAnchorElement, anchorStatementIfAll);

    @PsiModifier.ModifierConstant String visibility = JavaRefactoringSettings.getInstance().INTRODUCE_FIELD_VISIBILITY;
    if (visibility == null) {
      visibility = PsiModifier.PRIVATE;
    }
    SuggestedNameInfo suggestedName = JavaNameSuggestionUtil.suggestFieldName(
      successContext.tempType(), null, successContext.selectedExpr(),
      parameters.declareStatic(), successContext.parentClass());
    String name = suggestedName.names.length > 0 ? suggestedName.names[0] : "myField";

    boolean declareFinal = place == JavaIntroduceFieldService.InitializationPlace.IN_FIELD_DECLARATION ||
                           (place == JavaIntroduceFieldService.InitializationPlace.IN_CONSTRUCTOR && !parameters.declareStatic()) ||
                           (place == JavaIntroduceFieldService.InitializationPlace.IN_CURRENT_METHOD &&
                            parameters.currentMethodConstructor() &&
                            successContext.parentClass().getConstructors().length <= 1);
    BaseExpressionToFieldHandler.Settings settings = new BaseExpressionToFieldHandler.Settings(name,
                                                                                               successContext.selectedExpr(),
                                                                                               occurrences,
                                                                                               false,
                                                                                               parameters.declareStatic(),
                                                                                               declareFinal,
                                                                                               place,
                                                                                               visibility,
                                                                                               null,
                                                                                               successContext.tempType(),
                                                                                               false,
                                                                                               successContext.parentClass(), false, false);

    if (expression.getUserData(ElementToWorkOn.REPLACE_NON_PHYSICAL) == Boolean.TRUE) {
      ElementToWorkOn.REPLACE_NON_PHYSICAL.set(tempAnchorElement, true);
      ElementToWorkOn.REPLACE_NON_PHYSICAL.set(anchorStatementIfAll, true);
      ElementToWorkOn.REPLACE_NON_PHYSICAL.set(successContext.selectedExpr(), true);
      ElementToWorkOn.REPLACE_NON_PHYSICAL.set(successContext.parentClass(), true);
      Arrays.stream(occurrences).forEach(e -> ElementToWorkOn.REPLACE_NON_PHYSICAL.set(e, true));
    }

    final BaseExpressionToFieldHandler.ConvertToFieldRunnable runnable =
      new BaseExpressionToFieldHandler.ConvertToFieldRunnable(successContext.selectedExpr(), settings, successContext.tempType(),
                                                              occurrences,
                                                              anchorStatementIfAll, tempAnchorElement, null, successContext.parentClass());
    runnable.run();
    PsiField result = runnable.getField();
    addExtraNewLine(result);
    return result;
  }

  private static void addExtraNewLine(@Nullable PsiField result) {
    if (result == null) {
      return;
    }
    PsiClass containingClass = result.getContainingClass();
    Document document = result.getContainingFile().getViewProvider().getDocument();
    if (document != null) {
      PsiDocumentManager.getInstance(result.getProject()).doPostponedOperationsAndUnblockDocument(document);
    }
    if (containingClass != null && containingClass.isValid()) {
      PsiElement lBrace = containingClass.getLBrace();
      if (lBrace == null) {
        return;
      }
      PsiElement ws = lBrace.getNextSibling();
      if (ws instanceof PsiWhiteSpace && ws.getNextSibling() == result) {
        String wsText = ws.getText();
        if (wsText.startsWith("\n") && !wsText.startsWith("\n\n")) {
          ws.replace(PsiParserFacade.getInstance(result.getProject()).createWhiteSpaceFromText("\n" + wsText));
        }
      }
    }
  }

  static @NotNull SettingParameters getParameters(@Nullable PsiClass parentClass,
                                                  @Nullable PsiExpression expr,
                                                  PsiExpression @NotNull [] occurrences,
                                                  @NotNull PsiElement anchorElement,
                                                  @Nullable PsiElement anchorElementIfAll) {
    final PsiMethod containingMethod = PsiTreeUtil.getParentOfType(expr != null ? expr : anchorElement, PsiMethod.class);
    final PsiModifierListOwner staticParentElement = PsiUtil.getEnclosingStaticElement(getElement(expr, anchorElement), parentClass);
    boolean declareStatic = staticParentElement != null || parentClass != null && parentClass.isRecord();

    boolean isInSuperOrThis = false;
    if (!declareStatic) {
      for (int i = 0; !declareStatic && i < occurrences.length; i++) {
        declareStatic = isInSuperOrThis = isInSuperOrThis(occurrences[i]);
      }
    }
    if (isInSuperOrThis && PsiUtil.isAvailable(JavaFeature.STATIC_INTERFACE_CALLS, expr != null ? expr : anchorElement)) {
      isInSuperOrThis = false;
    }
    int occurrencesNumber = occurrences.length;
    final boolean currentMethodConstructor = containingMethod != null && containingMethod.isConstructor();
    final boolean allowInitInMethod = (!currentMethodConstructor || !isInSuperOrThis) &&
                                      (anchorElement instanceof PsiLocalVariable || anchorElement instanceof PsiStatement);
    final boolean allowInitInMethodIfAll = (!currentMethodConstructor || !isInSuperOrThis) && anchorElementIfAll instanceof PsiStatement;
    SettingParameters parameter =
      new SettingParameters(containingMethod, declareStatic, occurrencesNumber, currentMethodConstructor, allowInitInMethod,
                            allowInitInMethodIfAll);
    return parameter;
  }

  record SettingParameters(@Nullable PsiMethod containingMethod,
                           boolean declareStatic,
                           int occurrencesNumber,
                           boolean currentMethodConstructor,
                           boolean allowInitInMethod,
                           boolean allowInitInMethodIfAll) {
  }


  static boolean isInSuperOrThis(@NotNull PsiExpression occurrence) {
    return !NotInConstructorCallFilter.INSTANCE.isOK(occurrence);
  }

  @Nullable
  private static PsiType getTypeByExpression(@NotNull PsiExpression expr) {
    return CommonJavaRefactoringUtil.getTypeByExpressionWithExpectedType(expr);
  }

  private static @Nullable @NlsContexts.DialogMessage String checkParentClass(@Nullable PsiClass parentClass,
                                                                              @NotNull PsiFile file,
                                                                              @NotNull BaseExpressionToFieldHandler handler) {
    if (parentClass == null) {
      if (FileTypeUtils.isInServerPageFile(file)) {
        return JavaRefactoringBundle.message("error.not.supported.for.jsp", handler.getRefactoringName());
      }
      else if ("package-info.java".equals(file.getName())) {
        return JavaRefactoringBundle.message("error.not.supported.for.package.info", handler.getRefactoringName());
      }
      else {
        LOG.error("Unexpected file: " + file);
        return null;
      }
    }
    return null;
  }


  private static PsiElement getElement(PsiExpression expr, PsiElement anchorElement) {
    PsiElement element = null;
    if (expr != null) {
      element = expr.getUserData(ElementToWorkOn.PARENT);
      if (element == null) element = expr;
    }
    if (element == null) element = anchorElement;
    return element;
  }
}
