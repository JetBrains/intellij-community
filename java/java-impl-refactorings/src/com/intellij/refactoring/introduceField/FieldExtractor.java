// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.introduceField;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.TestFrameworks;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.LambdaUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiParserFacade;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.FileTypeUtils;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.NameSuggestionsGenerator;
import com.intellij.refactoring.util.JavaNameSuggestionUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.occurrences.NotInConstructorCallFilter;
import com.intellij.refactoring.util.occurrences.OccurrenceManager;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.intellij.refactoring.introduceField.IntroduceFieldCentralPanel.getInitializationPlaceParameters;
import static com.intellij.refactoring.introduceField.JavaIntroduceFieldService.AvailableSettings;
import static com.intellij.refactoring.introduceField.JavaIntroduceFieldService.InitializationPlace;
import static com.intellij.refactoring.introduceField.JavaIntroduceFieldService.ToFieldContext;
import static com.intellij.refactoring.introduceField.JavaIntroduceFieldService.VariableToFieldCandidatesContext;

final class FieldExtractor {
  private static final Logger LOG = Logger.getInstance(FieldExtractor.class);

  @NotNull private final BaseExpressionToFieldHandler myHandler;

  FieldExtractor(@NotNull BaseExpressionToFieldHandler handler) {
    myHandler = handler;
  }

  @NotNull ToFieldContext getContext(@NotNull PsiFile psiFile, @NotNull TextRange range) {
    int offset = range.getEndOffset();
    if (offset == 0) {
      return new ToFieldContext.Error(RefactoringBundle.message("refactoring.cannot.be.performed"));
    }

    PsiElement psiElementAfter = psiFile.findElementAt(offset);
    PsiElement psiElementBefore = psiFile.findElementAt(offset - 1);
    PsiElement expressionAfter = PsiTreeUtil.getParentOfType(psiElementAfter, PsiExpression.class);
    PsiElement expressionBefore = PsiTreeUtil.getParentOfType(psiElementBefore, PsiExpression.class);
    PsiElement psiElement = psiElementBefore;
    if (psiElementAfter != null && expressionAfter != null) {
      if (expressionBefore == null) {
        psiElement = psiElementAfter;
      }
    }
    ElementToWorkOn targetElement = null;
    if (range.getStartOffset() != range.getEndOffset()) {
      while (psiElement != null && psiElement.getTextRange() != null && !psiElement.getTextRange().equals(range)) {
        psiElement = psiElement.getParent();
      }
      if (psiElement != null && psiElement.getTextRange() != null && psiElement.getTextRange().equals(range)) {
        ElementToWorkOn elementToWorkOn = ElementToWorkOn.tryToCreate(psiElement);
        if (elementToWorkOn != null && myHandler.accept(elementToWorkOn)) {
          targetElement = elementToWorkOn;
        }
      }
    }
    else {
      while (true) {
        if (psiElement != null && psiElement.getTextRange() != null &&
            psiElement.getTextRange().contains(range)) {
          ElementToWorkOn elementToWorkOn = ElementToWorkOn.tryToCreate(psiElement);
          if (elementToWorkOn != null && myHandler.accept(elementToWorkOn)) {
            targetElement = elementToWorkOn;
            break;
          }
          psiElement = psiElement.getParent();
          continue;
        }
        break;
      }
    }
    if (targetElement == null) {
      return new ToFieldContext.Error(RefactoringBundle.message("refactoring.cannot.be.performed"));
    }
    if (targetElement.getExpression() != null) {
      return getContext(targetElement.getExpression());
    }
    if (targetElement.getLocalVariable() != null) {
      return getContext(targetElement.getLocalVariable());
    }
    return new ToFieldContext.Error(RefactoringBundle.message("refactoring.cannot.be.performed"));
  }

  @Nullable PsiField extractField(@NotNull PsiJavaFile file,
                                  @NotNull TextRange range,
                                  @NotNull InitializationPlace place) {
    ToFieldContext context = getContext(file, range);
    return switch (context) {
      case ToFieldContext.Error _ -> {
        yield null;
      }
      case ToFieldContext.ExpressionContext expressionContext -> {
        yield extractField(expressionContext, place);
      }
      case ToFieldContext.VariableContext variableContext -> {
        yield extractField(variableContext, place);
      }
    };
  }

  private @Nullable PsiField extractField(@NotNull ToFieldContext.VariableContext context,
                                          @NotNull InitializationPlace place) {
    PsiLocalVariable localVariable = context.localVariable();
    PsiLocalVariable local = localVariable;
    VariableToFieldCandidatesContext variableToFieldCandidatesContext = context.variableToFieldCandidatesContext();
    if (variableToFieldCandidatesContext.classes().isEmpty()) {
      return null;
    }
    PsiClass destinationClass = variableToFieldCandidatesContext.classes().getFirst();

    //todo workaround
    BaseExpressionToFieldHandler.Settings settings;
    final PsiExpression[] occurrences =
      CodeInsightUtil.findReferenceExpressions(CommonJavaRefactoringUtil.getVariableScope(local), local);

    //todo
    if (myHandler instanceof IntroduceConstantHandler) {
      boolean replaceAllOccurrences = true;
      Project project = localVariable.getProject();
      boolean preselectNonNls = PropertiesComponent.getInstance(project).getBoolean(IntroduceConstantDialog.NONNLS_SELECTED_PROPERTY);
      PsiType defaultType = localVariable.getType();
      defaultType = PsiTypesUtil.removeExternalAnnotations(defaultType);

      final String propertyName =
        JavaCodeStyleManager.getInstance(project).variableNameToPropertyName(localVariable.getName(), VariableKind.LOCAL_VARIABLE);

      PsiExpression expr = local.getInitializer();
      NameSuggestionsGenerator generator = IntroduceConstantDialog.createNameSuggestionGenerator(propertyName, expr, JavaCodeStyleManager.getInstance(project), null,
                                                              destinationClass);
      settings = new BaseExpressionToFieldHandler.Settings(generator.getSuggestedNameInfo(defaultType).names[0], expr, occurrences,
                                                           replaceAllOccurrences, true, true,
                                                           place,
                                                           ObjectUtils.notNull(
                                                             JavaRefactoringSettings.getInstance().INTRODUCE_CONSTANT_VISIBILITY,
                                                             PsiModifier.PUBLIC), local, defaultType, true, destinationClass,
                                                           preselectNonNls, false);
    }
    else {
      PsiExpression expr = local.getInitializer();
      PsiType defaultType = localVariable.getType();

      @PsiModifier.ModifierConstant String visibility = JavaRefactoringSettings.getInstance().INTRODUCE_FIELD_VISIBILITY;
      if (visibility == null) {
        visibility = PsiModifier.PRIVATE;
      }
      final PsiStatement statement = PsiTreeUtil.getParentOfType(local, PsiStatement.class);
      FieldExtractor.SettingParameters parameters =
        getParameters(destinationClass, expr, occurrences, local, statement, false);

      settings = new BaseExpressionToFieldHandler.Settings(local.getName(), expr, occurrences,
                                                           false, parameters.declareStatic(), true,
                                                           place,
                                                           visibility, local, defaultType, true, destinationClass,
                                                           false, false);
    }
    boolean rebindNeeded =
      !destinationClass.getManager().areElementsEquivalent(destinationClass, PsiTreeUtil.getParentOfType(local, PsiClass.class));

    final LocalToFieldHandler.IntroduceFieldRunnable runnable =
      new LocalToFieldHandler.IntroduceFieldRunnable(rebindNeeded, local, destinationClass, settings, occurrences);
    runnable.run();
    PsiField fieldResult = runnable.getField();
    addExtraNewLine(fieldResult);
    return fieldResult;
  }

  static ToFieldContext getContext(@NotNull BaseExpressionToFieldHandler handler,
                                   @NotNull PsiLocalVariable psiLocalVariable) {
    final PsiElement parent = psiLocalVariable.getParent();
    if (!(parent instanceof PsiDeclarationStatement)) {
      String message =
        RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("error.wrong.caret.position.local.or.expression.name"));
      return new ToFieldContext.Error(message);
    }

    if (psiLocalVariable.getContainingFile() instanceof PsiFile file && FileTypeUtils.isInServerPageFile(file)) {
      String message = JavaRefactoringBundle.message("error.not.supported.for.jsp", handler.getRefactoringName());
      return new ToFieldContext.Error(message);
    }

    String validationMessage = handler.checkLocalVariables(psiLocalVariable);
    if (validationMessage != null) {
      return new ToFieldContext.Error(validationMessage);
    }
    VariableToFieldCandidatesContext
      variableToFieldCandidatesContext =
      LocalToFieldHandler.getCandidatesContext(psiLocalVariable, handler instanceof IntroduceConstantHandler);
    if (variableToFieldCandidatesContext.classes().isEmpty()) {
      PsiClass parentClass = PsiTreeUtil.getParentOfType(psiLocalVariable, PsiClass.class);
      if (parentClass == null) {
        String message =
          RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("error.wrong.caret.position.local.or.expression.name"));
        return new ToFieldContext.Error(message);
      }
      String message = IntroduceFieldHandler.checkCanIntroduceField(parentClass, psiLocalVariable.getType());
      if (message != null) {
        return new ToFieldContext.Error(RefactoringBundle.getCannotRefactorMessage(message));
      }
      message =
        RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("error.wrong.caret.position.local.or.expression.name"));
      return new ToFieldContext.Error(message);
    }
    return new ToFieldContext.VariableContext(psiLocalVariable, variableToFieldCandidatesContext);
  }

  static ToFieldContext getContext(@NotNull BaseExpressionToFieldHandler handler,
                                   @NotNull PsiExpression selectedExpr) {
    if (!Comparing.strEqual(IntroduceConstantHandler.getRefactoringNameText(), handler.getRefactoringName()) &&
        isInSuperOrThis(selectedExpr) &&
        handler.isStaticFinalInitializer(selectedExpr) != null) {
      return new ToFieldContext.Error(
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
      return new ToFieldContext.Error(message);
    }

    if (PsiTypes.voidType().equals(tempType)) {
      String message = RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("selected.expression.has.void.type"));
      return new ToFieldContext.Error(message);
    }

    String switchLabelError = RefactoringUtil.checkEnumConstantInSwitchLabel(selectedExpr);
    if (switchLabelError != null) {
      String message = RefactoringBundle.getCannotRefactorMessage(switchLabelError);
      return new ToFieldContext.Error(message);
    }
    PsiClass parentClass = handler.getParentClass(selectedExpr);
    String message = checkParentClass(parentClass, file, handler);
    if (message != null) {
      return new ToFieldContext.Error(message);
    }
    if (parentClass == null) {
      return new ToFieldContext.Error(JavaRefactoringBundle.message("selected.expression.cannot.be.extracted"));
    }
    final List<PsiClass> proposedClasses = new ArrayList<>();
    PsiClass aClass = parentClass;
    while (aClass != null) {
      proposedClasses.add(aClass);
      final PsiField psiField = BaseExpressionToFieldHandler.ConvertToFieldRunnable.checkForwardRefs(selectedExpr, aClass);
      if (psiField != null && psiField.getParent() == aClass) break;
      aClass = PsiTreeUtil.getParentOfType(aClass, PsiClass.class, true);
    }
    proposedClasses.removeIf(proposedClass -> proposedClass == null || handler.checkClass(proposedClass, selectedExpr) != null);
    if (proposedClasses.isEmpty()) {
      message = handler.checkClass(parentClass, selectedExpr);
      if (message != null) {
        return new ToFieldContext.Error(RefactoringBundle.getCannotRefactorMessage(message));
      }
      return new ToFieldContext.Error(JavaRefactoringBundle.message("selected.expression.cannot.be.extracted"));
    }

    return new ToFieldContext.ExpressionContext(selectedExpr, element, file, tempType, parentClass,
                                                proposedClasses);
  }


  @NotNull ToFieldContext getContext(@NotNull PsiExpression selectedExpr) {
    if (selectedExpr.getContainingFile() == null || !selectedExpr.isPhysical()) {
      return new ToFieldContext.Error(RefactoringBundle.message("refactoring.cannot.be.performed"));
    }
    return getContext(myHandler, selectedExpr);
  }

  @NotNull ToFieldContext getContext(@NotNull PsiLocalVariable selectedVariable) {
    if (selectedVariable.getContainingFile() == null || !selectedVariable.isPhysical()) {
      return new ToFieldContext.Error(RefactoringBundle.message("refactoring.cannot.be.performed"));
    }
    return getContext(myHandler, selectedVariable);
  }

  @NotNull AvailableSettings getAvailableSettings(@NotNull ToFieldContext.ExpressionContext context) {
    ArrayList<InitializationPlace> places =
      new ArrayList<>(List.of(InitializationPlace.values()));
    final OccurrenceManager occurrenceManager = myHandler.createOccurrenceManager(context.selectedExpr(), context.parentClass());
    final PsiExpression[] occurrences = occurrenceManager.getOccurrences();
    final PsiElement anchorStatementIfAll = occurrenceManager.getAnchorStatementForAll();
    PsiElement tempAnchorElement = CommonJavaRefactoringUtil.getParentExpressionAnchorElement(context.selectedExpr());
    SettingParameters parameters = getParameters(context.parentClass(), context.selectedExpr(), occurrences,
                                                 tempAnchorElement == null ? context.selectedExpr() : tempAnchorElement,
                                                 anchorStatementIfAll,
                                                 //todo
                                                 myHandler instanceof IntroduceConstantHandler);

    Project project = context.parentClass().getProject();
    boolean isTestClass = !DumbService.isDumb(project) && TestFrameworks.getInstance().isTestClass(context.parentClass());
    IntroduceFieldCentralPanel.InitializationParameters initializationPlaceParameters =
      getInitializationPlaceParameters(context.selectedExpr(), isTestClass);

    if (initializationPlaceParameters != null) {
      if (initializationPlaceParameters.locals()) {
        places.remove(InitializationPlace.IN_FIELD_DECLARATION);
      }
      if (!initializationPlaceParameters.constructor()) {
        places.remove(InitializationPlace.IN_CONSTRUCTOR);
      }
      if (!initializationPlaceParameters.insetup()) {
        places.remove(InitializationPlace.IN_SETUP_METHOD);
      }
    }

    if (!isTestClass) {
      places.remove(InitializationPlace.IN_SETUP_METHOD);
    }

    if (!parameters.allowInitInMethod()) {
      places.remove(InitializationPlace.IN_CURRENT_METHOD);
    }
    boolean inOnlyConstructor = parameters.currentMethodConstructor() && context.parentClass().getConstructors().length == 1;
    if (parameters.declareStatic() || inOnlyConstructor) {
      places.remove(InitializationPlace.IN_CONSTRUCTOR);
    }

    return new AvailableSettings(places);
  }

  @Nullable PsiField extractField(@NotNull ToFieldContext.ExpressionContext expressionContextContext,
                                  @NotNull InitializationPlace place) {
    final OccurrenceManager occurrenceManager =
      myHandler.createOccurrenceManager(expressionContextContext.selectedExpr(), expressionContextContext.parentClass());
    final PsiExpression[] occurrences = occurrenceManager.getOccurrences();
    final PsiElement anchorStatementIfAll = occurrenceManager.getAnchorStatementForAll();

    PsiElement tempAnchorElement = CommonJavaRefactoringUtil.getParentExpressionAnchorElement(expressionContextContext.selectedExpr());
    if (tempAnchorElement == null) {
      tempAnchorElement = expressionContextContext.selectedExpr();
    }
    SettingParameters parameters =
      getParameters(expressionContextContext.parentClass(), expressionContextContext.selectedExpr(), occurrences, tempAnchorElement,
                    anchorStatementIfAll,
                    // todo
                    myHandler instanceof IntroduceConstantHandler);

    //noinspection MagicConstant
    @PsiModifier.ModifierConstant String visibility = JavaRefactoringSettings.getInstance().INTRODUCE_FIELD_VISIBILITY;
    if (visibility == null) {
      visibility = PsiModifier.PRIVATE;
    }
    //todo workaround
    if (myHandler instanceof IntroduceConstantHandler) {
      visibility = PsiModifier.PUBLIC;
    }

    SuggestedNameInfo suggestedName =
      JavaNameSuggestionUtil.suggestFieldName(expressionContextContext.tempType(), null, expressionContextContext.selectedExpr(),
                                              parameters.declareStatic(), expressionContextContext.parentClass());

    //todo extract similar code

    if (myHandler instanceof IntroduceConstantHandler) {
      JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(expressionContextContext.selectedExpr().getProject());
      NameSuggestionsGenerator generator =
        IntroduceConstantDialog.createNameSuggestionGenerator(null, expressionContextContext.selectedExpr(), codeStyleManager, null, expressionContextContext.parentClass());
      suggestedName =  generator.getSuggestedNameInfo(expressionContextContext.tempType());

    }

    String name = suggestedName.names.length > 0 ? suggestedName.names[0] : "myField";

    boolean declareFinal = place == InitializationPlace.IN_FIELD_DECLARATION ||
                           (place == InitializationPlace.IN_CONSTRUCTOR && !parameters.declareStatic()) ||
                           (place == InitializationPlace.IN_CURRENT_METHOD &&
                            parameters.currentMethodConstructor() &&
                            expressionContextContext.parentClass().getConstructors().length <= 1);
    //todo workaround
    boolean preselectNonNls = myHandler instanceof IntroduceConstantHandler &&
                              PropertiesComponent.getInstance(expressionContextContext.selectedExpr().getProject())
                                .getBoolean(IntroduceConstantDialog.NONNLS_SELECTED_PROPERTY);

    BaseExpressionToFieldHandler.Settings settings =
      new BaseExpressionToFieldHandler.Settings(name, expressionContextContext.selectedExpr(), occurrences, false,
                                                parameters.declareStatic(), declareFinal, place, visibility, null,
                                                //todo workaround
                                                expressionContextContext.tempType(), myHandler instanceof IntroduceConstantHandler, expressionContextContext.parentClass(),
                                                preselectNonNls, false);

    if (expressionContextContext.selectedExpr().getUserData(ElementToWorkOn.REPLACE_NON_PHYSICAL) == Boolean.TRUE) {
      ElementToWorkOn.REPLACE_NON_PHYSICAL.set(tempAnchorElement, true);
      ElementToWorkOn.REPLACE_NON_PHYSICAL.set(anchorStatementIfAll, true);
      ElementToWorkOn.REPLACE_NON_PHYSICAL.set(expressionContextContext.selectedExpr(), true);
      ElementToWorkOn.REPLACE_NON_PHYSICAL.set(expressionContextContext.parentClass(), true);
      Arrays.stream(occurrences).forEach(e -> ElementToWorkOn.REPLACE_NON_PHYSICAL.set(e, true));
    }

    final BaseExpressionToFieldHandler.ConvertToFieldRunnable runnable =
      new BaseExpressionToFieldHandler.ConvertToFieldRunnable(expressionContextContext.selectedExpr(), settings,
                                                              expressionContextContext.tempType(), occurrences, anchorStatementIfAll,
                                                              tempAnchorElement, null, expressionContextContext.parentClass());
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
                                                  @Nullable PsiElement anchorElementIfAll,
                                                  boolean forceStatic) {
    final PsiMethod containingMethod = PsiTreeUtil.getParentOfType(expr != null ? expr : anchorElement, PsiMethod.class);
    final PsiModifierListOwner staticParentElement = PsiUtil.getEnclosingStaticElement(getElement(expr, anchorElement), parentClass);
    boolean declareStatic = forceStatic || staticParentElement != null || parentClass != null && parentClass.isRecord();

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

  record SettingParameters(@Nullable PsiMethod containingMethod, boolean declareStatic, int occurrencesNumber,
                           boolean currentMethodConstructor, boolean allowInitInMethod, boolean allowInitInMethodIfAll) {
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
