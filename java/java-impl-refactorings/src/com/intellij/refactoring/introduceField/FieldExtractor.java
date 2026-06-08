// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.introduceField;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.TestFrameworks;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.LambdaUtil;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiCallExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.psi.PsiMethodReferenceUtil;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiParserFacade;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiThisExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.util.FileTypeUtils;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.IntroduceVariableUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.classMembers.ClassMemberReferencesVisitor;
import com.intellij.refactoring.util.occurrences.NotInConstructorCallFilter;
import com.intellij.refactoring.util.occurrences.OccurrenceManager;
import com.intellij.util.CommonJavaRefactoringUtil;
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


/**
 * Headless core of the "Introduce Field" / "Introduce Constant" Java refactorings.
 * <p>
 * Like {@link FieldHelper}, <b>this class does not use or invoke any UI</b>:
 * it neither opens dialogs nor shows popups/messages. All choices are derived
 * from PSI and from persisted refactoring settings, so {@code FieldExtractor}
 * can be driven from non-interactive contexts such as {@code ModCommand}
 * actions and the LSP language server.
 */
final class FieldExtractor {
  private static final Logger LOG = Logger.getInstance(FieldExtractor.class);

  @NotNull private final FieldHelper myHelper;

  FieldExtractor(@NotNull FieldHelper helper) {
    myHelper = helper;
  }

  @NotNull ToFieldContext getContext(@NotNull PsiFile psiFile, @NotNull TextRange range) {
    int offset = range.getEndOffset();
    if (offset == 0) {
      return new ToFieldContext.Error(RefactoringBundle.message("refactoring.cannot.be.performed"));
    }
    ElementToWorkOn targetElement = findElementToWorkOn(psiFile, range);
    if (targetElement == null) {
      return new ToFieldContext.Error(RefactoringBundle.message("refactoring.cannot.be.performed"));
    }
    if (targetElement.getExpression() != null) {
      return getContext(psiFile, targetElement.getExpression());
    }
    if (targetElement.getLocalVariable() != null) {
      return getContext(psiFile, targetElement.getLocalVariable());
    }
    return new ToFieldContext.Error(RefactoringBundle.message("refactoring.cannot.be.performed"));
  }

  private @Nullable ElementToWorkOn findElementToWorkOn(@NotNull PsiFile psiFile, @NotNull TextRange range) {
    int offset = range.getEndOffset();
    PsiElement psiElement;
    if (range.getEndOffset() == range.getStartOffset()) {
      PsiElement psiElementAfter = psiFile.findElementAt(offset);
      PsiElement psiElementBefore = psiFile.findElementAt(offset - 1);
      PsiElement expressionAfter = getExpression(psiElementAfter);
      PsiElement expressionBefore = getExpression(psiElementBefore);
      psiElement = psiElementBefore;
      if (psiElementAfter != null && expressionAfter != null) {
        if (expressionBefore == null) {
          psiElement = psiElementAfter;
        }
        else if (PsiTreeUtil.isAncestor(expressionBefore, expressionAfter, true)) {
          psiElement = psiElementAfter;
        }
      }
    }
    else {
      psiElement = psiFile.findElementAt(range.getStartOffset());
    }

    ElementToWorkOn targetElement = null;
    if (range.getStartOffset() != range.getEndOffset()) {
      while (psiElement != null && psiElement.getTextRange() != null && !psiElement.getTextRange().equals(range)) {
        psiElement = psiElement.getParent();
      }
      if (psiElement != null && psiElement.getTextRange() != null && psiElement.getTextRange().equals(range)) {
        targetElement = ElementToWorkOn.tryToCreate(psiElement);
      }
      if (targetElement == null) {
        targetElement = ElementToWorkOn.tryToCreate(
          IntroduceVariableUtil.getSelectedExpression(psiFile.getProject(), psiFile, range.getStartOffset(), range.getEndOffset()));
      }
    }
    else {
      while (true) {
        if (psiElement != null && psiElement.getTextRange() != null &&
            psiElement.getTextRange().contains(range)) {
          ElementToWorkOn elementToWorkOn = ElementToWorkOn.tryToCreate(psiElement);
          if (elementToWorkOn != null && myHelper.accept(elementToWorkOn)) {
            targetElement = elementToWorkOn;
            break;
          }
          psiElement = psiElement.getParent();
          continue;
        }
        break;
      }
    }
    return targetElement;
  }

  private static @Nullable PsiElement getExpression(PsiElement psiElementAfter) {
    PsiElement expressionAfter = PsiTreeUtil.getParentOfType(psiElementAfter, PsiExpression.class);
    if (expressionAfter instanceof PsiReferenceExpression referenceExpression &&
        referenceExpression.getParent() instanceof PsiMethodCallExpression methodCallExpression &&
        methodCallExpression.getMethodExpression() == referenceExpression
    ) {
      expressionAfter = methodCallExpression;
    }
    return expressionAfter;
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
    PsiLocalVariable local = context.localVariable();
    VariableToFieldCandidatesContext variableToFieldCandidatesContext = context.variableToFieldCandidatesContext();
    if (variableToFieldCandidatesContext.classes().isEmpty()) {
      return null;
    }
    PsiClass destinationClass = variableToFieldCandidatesContext.classes().getFirst();

    final PsiExpression[] occurrences =
      CodeInsightUtil.findReferenceExpressions(CommonJavaRefactoringUtil.getVariableScope(local), local);

    BaseExpressionToFieldHandler.Settings settings = myHelper.getSettings(context, place, occurrences);
    boolean rebindNeeded =
      !destinationClass.getManager().areElementsEquivalent(destinationClass, PsiTreeUtil.getParentOfType(local, PsiClass.class));

    final LocalToFieldHandler.IntroduceFieldRunnable runnable =
      new LocalToFieldHandler.IntroduceFieldRunnable(rebindNeeded, local, destinationClass, settings, occurrences);
    runnable.run();
    PsiField fieldResult = runnable.getField();
    addExtraNewLine(fieldResult);
    return fieldResult;
  }

  static ToFieldContext getContext(@NotNull FieldHelper helper,
                                   @NotNull PsiLocalVariable psiLocalVariable,
                                   boolean checkType) {
    final PsiElement parent = psiLocalVariable.getParent();
    if (!(parent instanceof PsiDeclarationStatement)) {
      String message =
        RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("error.wrong.caret.position.local.or.expression.name"));
      return new ToFieldContext.Error(message);
    }

    if (psiLocalVariable.getContainingFile() instanceof PsiFile file && FileTypeUtils.isInServerPageFile(file)) {
      String message = JavaRefactoringBundle.message("error.not.supported.for.jsp", helper.getRefactoringName());
      return new ToFieldContext.Error(message);
    }

    String validationMessage = helper.checkOccurrences(psiLocalVariable);
    if (validationMessage != null) {
      return new ToFieldContext.Error(validationMessage);
    }
    FieldHelper.InvalidInitializer invalidInitializer = helper.checkInitializer(null, psiLocalVariable);
    if (checkType && invalidInitializer != null) {
      return new ToFieldContext.Error(invalidInitializer.message());
    }
    VariableToFieldCandidatesContext
      variableToFieldCandidatesContext =
      LocalToFieldHandler.getCandidatesContext(psiLocalVariable, helper.isConstant());
    if (variableToFieldCandidatesContext.classes().isEmpty()) {
      PsiClass parentClass = PsiTreeUtil.getParentOfType(psiLocalVariable, PsiClass.class);
      if (parentClass == null) {
        String message =
          RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("error.wrong.caret.position.local.or.expression.name"));
        return new ToFieldContext.Error(message);
      }
      String message = IntroduceFieldHelper.checkCanIntroduceField(parentClass, psiLocalVariable.getType());
      if (message != null) {
        return new ToFieldContext.Error(RefactoringBundle.getCannotRefactorMessage(message));
      }
      message =
        RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("error.wrong.caret.position.local.or.expression.name"));
      return new ToFieldContext.Error(message);
    }
    return new ToFieldContext.VariableContext(psiLocalVariable, variableToFieldCandidatesContext);
  }

  static ToFieldContext getContext(@NotNull FieldHelper helper,
                                   @NotNull PsiExpression selectedExpr,
                                   boolean checkType) {
    if (!helper.isConstant() &&
        isInSuperOrThis(selectedExpr) &&
        isStaticFinalInitializer(selectedExpr, helper.isConstant()) != null) {
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
    PsiClass parentClass = helper.getParentClass(selectedExpr);
    String message = checkParentClass(parentClass, file, helper);
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
    proposedClasses.removeIf(proposedClass -> proposedClass == null || helper.checkClass(proposedClass, selectedExpr) != null);
    if (proposedClasses.isEmpty()) {
      message = helper.checkClass(parentClass, selectedExpr);
      if (message != null) {
        return new ToFieldContext.Error(RefactoringBundle.getCannotRefactorMessage(message));
      }
      return new ToFieldContext.Error(JavaRefactoringBundle.message("selected.expression.cannot.be.extracted"));
    }

    String validationMessage = helper.checkOccurrences(selectedExpr, parentClass);
    if (validationMessage != null) {
      return new ToFieldContext.Error(validationMessage);
    }

    FieldHelper.InvalidInitializer invalidInitializer = helper.checkInitializer(selectedExpr, null);
    if (checkType && invalidInitializer != null) {
      return new ToFieldContext.Error(invalidInitializer.message());
    }

    if (checkType && selectedExpr.getType() == null &&
        !(selectedExpr.getParent() instanceof PsiAssignmentExpression assignmentExpression &&
          (assignmentExpression.getLExpression() == selectedExpr ||
           assignmentExpression.getRExpression() == null ||
           assignmentExpression.getRExpression().getType() == null))) {
      return new ToFieldContext.Error(
        RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("invalid.expression.context")));
    }

    return new ToFieldContext.ExpressionContext(selectedExpr, element, file, tempType, parentClass,
                                                proposedClasses);
  }


  @NotNull ToFieldContext getContext(@Nullable PsiFile psiFile, @NotNull PsiExpression selectedExpr) {
    if (psiFile == null) {
      return new ToFieldContext.Error(RefactoringBundle.message("refactoring.cannot.be.performed"));
    }
    return getContext(myHelper, selectedExpr, true);
  }

  @NotNull ToFieldContext getContext(@Nullable PsiFile psiFile, @NotNull PsiLocalVariable selectedVariable) {
    if (psiFile == null) {
      return new ToFieldContext.Error(RefactoringBundle.message("refactoring.cannot.be.performed"));
    }
    return getContext(myHelper, selectedVariable, true);
  }

  @NotNull AvailableSettings getAvailableSettings(@NotNull ToFieldContext.VariableContext context) {
    PsiExpression expr = context.localVariable().getInitializer();
    if (expr == null) return new AvailableSettings(List.of());
    List<PsiClass> classes = context.variableToFieldCandidatesContext().classes();
    if (classes.isEmpty()) return new AvailableSettings(List.of());
    PsiClass parentClass = classes.getFirst();
    return getAvailableSettings(expr, parentClass);
  }

  @NotNull AvailableSettings getAvailableSettings(@NotNull ToFieldContext.ExpressionContext context) {
    PsiExpression expr = context.selectedExpr();
    PsiClass parentClass = context.parentClass();
    return getAvailableSettings(expr, parentClass);
  }

  private @NotNull AvailableSettings getAvailableSettings(@NotNull PsiExpression expr, @NotNull PsiClass parentClass) {
    ArrayList<InitializationPlace> places =
      new ArrayList<>(List.of(InitializationPlace.values()));
    final OccurrenceManager occurrenceManager = myHelper.createOccurrenceManager(expr, parentClass);
    final PsiExpression[] occurrences = occurrenceManager.getOccurrences();
    final PsiElement anchorStatementIfAll = occurrenceManager.getAnchorStatementForAll();
    PsiElement tempAnchorElement = CommonJavaRefactoringUtil.getParentExpressionAnchorElement(expr);
    SettingParameters parameters = getParameters(parentClass, expr, occurrences,
                                                 tempAnchorElement == null ? expr : tempAnchorElement,
                                                 anchorStatementIfAll,
                                                 myHelper.isConstant());

    Project project = parentClass.getProject();
    boolean isTestClass = !DumbService.isDumb(project) && TestFrameworks.getInstance().isTestClass(parentClass);
    IntroduceFieldCentralPanel.InitializationParameters initializationPlaceParameters =
      getInitializationPlaceParameters(expr, isTestClass);

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
    boolean inOnlyConstructor = parameters.currentMethodConstructor() && parentClass.getConstructors().length == 1;
    if (parameters.declareStatic() || inOnlyConstructor) {
      places.remove(InitializationPlace.IN_CONSTRUCTOR);
    }

    return new AvailableSettings(places);
  }

  @Nullable PsiField extractField(@NotNull ToFieldContext.ExpressionContext expressionContextContext,
                                  @NotNull InitializationPlace place) {
    final OccurrenceManager occurrenceManager =
      myHelper.createOccurrenceManager(expressionContextContext.selectedExpr(), expressionContextContext.parentClass());
    final PsiExpression[] occurrences = occurrenceManager.getOccurrences();
    final PsiElement anchorStatementIfAll = occurrenceManager.getAnchorStatementForAll();

    PsiElement tempAnchorElement = CommonJavaRefactoringUtil.getParentExpressionAnchorElement(expressionContextContext.selectedExpr());
    if (tempAnchorElement == null) {
      tempAnchorElement = expressionContextContext.selectedExpr();
    }
    SettingParameters parameters =
      getParameters(expressionContextContext.parentClass(), expressionContextContext.selectedExpr(), occurrences, tempAnchorElement,
                    anchorStatementIfAll,
                    myHelper.isConstant());

    @PsiModifier.ModifierConstant String visibility = myHelper.getVisibility();

    SuggestedNameInfo suggestedName = myHelper.getSuggestedNameInfo(expressionContextContext, parameters);

    String name = suggestedName.names.length > 0 ? suggestedName.names[0] : "myField";

    boolean declareFinal = place == InitializationPlace.IN_FIELD_DECLARATION ||
                           (place == InitializationPlace.IN_CONSTRUCTOR && !parameters.declareStatic()) ||
                           (place == InitializationPlace.IN_CURRENT_METHOD &&
                            parameters.currentMethodConstructor() &&
                            expressionContextContext.parentClass().getConstructors().length <= 1);

    boolean preselectNonNls = myHelper.isConstant() &&
                              PropertiesComponent.getInstance(expressionContextContext.selectedExpr().getProject())
                                .getBoolean(IntroduceConstantDialog.NONNLS_SELECTED_PROPERTY);

    BaseExpressionToFieldHandler.Settings settings =
      new BaseExpressionToFieldHandler.Settings(name, expressionContextContext.selectedExpr(), occurrences, false,
                                                parameters.declareStatic(), declareFinal, place, visibility, null,
                                                expressionContextContext.tempType(), myHelper.isConstant(),
                                                expressionContextContext.parentClass(),
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
                                                                              @NotNull FieldHelper helper) {
    if (parentClass == null) {
      if (FileTypeUtils.isInServerPageFile(file)) {
        return JavaRefactoringBundle.message("error.not.supported.for.jsp", helper.getRefactoringName());
      }
      else if ("package-info.java".equals(file.getName())) {
        return JavaRefactoringBundle.message("error.not.supported.for.package.info", helper.getRefactoringName());
      }
    }
    return null;
  }


  private static PsiElement getElement(@Nullable PsiExpression expr, @Nullable PsiElement anchorElement) {
    PsiElement element = null;
    if (expr != null) {
      element = expr.getUserData(ElementToWorkOn.PARENT);
      if (element == null) element = expr;
    }
    if (element == null) element = anchorElement;
    return element;
  }

  static @Nullable PsiClass getParentClass(@NotNull PsiExpression initializerExpression, boolean isConstant) {
    boolean compileTimeConstant = LocalToFieldHandler.isCompileTimeConstant(initializerExpression, initializerExpression.getType());
    PsiElement parent = initializerExpression.getUserData(ElementToWorkOn.PARENT);
    PsiClass aClass = PsiTreeUtil.getParentOfType((parent == null) ? initializerExpression : parent, PsiClass.class);
    while (aClass != null) {
      if (!isConstant || compileTimeConstant || LocalToFieldHandler.isStaticFieldAllowed(aClass)) return aClass;
      aClass = PsiTreeUtil.getParentOfType(aClass, PsiClass.class);
    }
    return null;
  }

  static @Nullable PsiElement isStaticFinalInitializer(@Nullable PsiExpression expr, boolean isConstant) {
    PsiClass parentClass = expr != null ? getParentClass(expr, isConstant) : null;
    if (parentClass == null) return null;
    IsStaticFinalInitializerExpression visitor = new IsStaticFinalInitializerExpression(parentClass, expr);
    expr.accept(visitor);
    return visitor.getElementReference();
  }

  private static class IsStaticFinalInitializerExpression extends ClassMemberReferencesVisitor {
    private PsiElement myElementReference;
    private final PsiExpression myInitializer;
    private boolean myCheckThrowables = true;

    IsStaticFinalInitializerExpression(PsiClass aClass, PsiExpression initializer) {
      super(aClass);
      myInitializer = initializer;
    }

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
      final PsiElement psiElement = expression.resolve();
      if ((PsiUtil.isJvmLocalVariable(psiElement)) &&
          !PsiTreeUtil.isAncestor(myInitializer, psiElement, false)) {
        myElementReference = expression;
      }
      else {
        super.visitReferenceExpression(expression);
      }
    }

    @Override
    public void visitMethodReferenceExpression(@NotNull PsiMethodReferenceExpression expression) {
      if (!PsiMethodReferenceUtil.isResolvedBySecondSearch(expression)) {
        super.visitMethodReferenceExpression(expression);
      }
    }

    @Override
    public void visitCallExpression(@NotNull PsiCallExpression callExpression) {
      super.visitCallExpression(callExpression);
      if (!myCheckThrowables) return;
      final List<PsiClassType> checkedExceptions = ExceptionUtil.getThrownCheckedExceptions(callExpression);
      if (!checkedExceptions.isEmpty()) {
        myElementReference = callExpression;
      }
    }

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      myCheckThrowables = false;
      super.visitClass(aClass);
    }

    @Override
    public void visitLambdaExpression(@NotNull PsiLambdaExpression expression) {
      myCheckThrowables = false;
      super.visitLambdaExpression(expression);
    }

    @Override
    protected void visitClassMemberReferenceElement(PsiMember classMember, PsiJavaCodeReferenceElement classMemberReference) {
      if (!classMember.hasModifierProperty(PsiModifier.STATIC)) {
        myElementReference = classMemberReference;
      }
    }

    @Override
    public void visitThisExpression(@NotNull PsiThisExpression expression) {
      myElementReference = expression;
    }

    @Override
    public void visitElement(@NotNull PsiElement element) {
      if (myElementReference != null) return;
      super.visitElement(element);
    }

    public @Nullable PsiElement getElementReference() {
      return myElementReference;
    }
  }
}
