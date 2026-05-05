// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.introduceField;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLambdaExpressionType;
import com.intellij.psi.PsiLambdaParameterType;
import com.intellij.psi.PsiParserFacade;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodReferenceType;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.AbstractJavaInplaceIntroducer;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.introduce.inplace.AbstractInplaceIntroducer;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.refactoring.util.JavaNameSuggestionUtil;
import com.intellij.refactoring.util.occurrences.ExpressionOccurrenceManager;
import com.intellij.refactoring.util.occurrences.NotInConstructorCallFilter;
import com.intellij.refactoring.util.occurrences.OccurrenceFilter;
import com.intellij.refactoring.util.occurrences.OccurrenceManager;
import com.intellij.util.CommonJavaRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.intellij.refactoring.introduceField.IntroduceFieldCentralPanel.getInitializationPlaceParameters;

public class IntroduceFieldHandler extends BaseExpressionToFieldHandler implements JavaIntroduceFieldHandlerBase {
  private InplaceIntroduceFieldPopup myInplaceIntroduceFieldPopup;

  public IntroduceFieldHandler() {
    super(false);
  }

  @Override
  protected String getRefactoringName() {
    return getRefactoringNameText();
  }

  @Override
  protected @Nullable String checkClass(@NotNull PsiClass parentClass, @NotNull PsiExpression selectedExpr) {
    return checkCanIntroduceField(parentClass, selectedExpr.getType());
  }

  /**
   * Checks if a field of the specified type can be created in the specified class.
   *
   * @param parentClass the class to create a field in
   * @param type        the type of the field that should be created
   * @param editor      to show error message for, if a problem is found
   * @return true, if a field can be introduced. false, if there is a problem.
   */
  static boolean canIntroduceField(@NotNull PsiClass parentClass, @Nullable PsiType type, Editor editor) {
    String message = checkCanIntroduceField(parentClass, type);
    if (message != null) {
      showErrorMessage(parentClass.getProject(), editor, message);
      return false;
    }
    return true;
  }

  private static @Nullable @NlsContexts.DialogMessage String checkCanIntroduceField(@NotNull PsiClass parentClass, @Nullable PsiType type) {
    if (parentClass.isInterface()) {
      return JavaRefactoringBundle.message("cannot.introduce.field.in.interface");
    }
    if (PsiTypes.nullType().equals(type) || type instanceof PsiLambdaParameterType || type instanceof PsiLambdaExpressionType ||
        type instanceof PsiMethodReferenceType) {
      return JavaRefactoringBundle.message("variable.type.unknown");
    }
    PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(type);
    if (aClass != null && PsiUtil.isLocalClass(aClass) && !PsiTreeUtil.isAncestor(aClass, parentClass, false)) {
      return JavaRefactoringBundle.message("0.is.not.visible.to.members.of.1",
                                           RefactoringUIUtil.getDescription(aClass, false),
                                           RefactoringUIUtil.getDescription(parentClass, false));
    }
    return null;
  }

  private static void showErrorMessage(@NotNull Project project, Editor editor, @NlsContexts.DialogMessage String message) {
    message = RefactoringBundle.getCannotRefactorMessage(message);
    CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringNameText(), HelpID.INTRODUCE_FIELD);
  }

  @Override
  protected String getHelpID() {
    return HelpID.INTRODUCE_FIELD;
  }

  @Override
  public void invoke(final @NotNull Project project, final Editor editor, PsiFile file, DataContext dataContext) {
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, file)) return;
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    ElementToWorkOn.processElementToWorkOn(editor, file, getRefactoringNameText(), HelpID.INTRODUCE_FIELD, project,
                                           getElementProcessor(project, editor));
  }

  @Override
  protected Settings showRefactoringDialog(Project project, Editor editor, PsiClass parentClass, PsiExpression expr,
                                           PsiType type,
                                           PsiExpression[] occurrences, PsiElement anchorElement, PsiElement anchorElementIfAll) {
    final AbstractInplaceIntroducer activeIntroducer = AbstractInplaceIntroducer.getActiveIntroducer(editor);

    ElementToWorkOn elementToWorkOn = ElementToWorkOn.adjustElements(expr, anchorElement);
    PsiLocalVariable localVariable = elementToWorkOn.getLocalVariable();
    expr = elementToWorkOn.getExpression();

    String enteredName = null;
    boolean replaceAll = false;
    if (activeIntroducer != null) {
      activeIntroducer.stopIntroduce(editor);
      expr = (PsiExpression)activeIntroducer.getExpr();
      localVariable = (PsiLocalVariable)activeIntroducer.getLocalVariable();
      occurrences = (PsiExpression[])activeIntroducer.getOccurrences();
      enteredName = activeIntroducer.getInputName();
      replaceAll = activeIntroducer.isReplaceAllOccurrences();
      type = ((AbstractJavaInplaceIntroducer)activeIntroducer).getType();
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      IntroduceFieldDialog.ourLastInitializerPlace = ((InplaceIntroduceFieldPopup)activeIntroducer).getInitializerPlace();
    }

    SettingParameters parameters = getParameters(parentClass, expr, occurrences, anchorElement, anchorElementIfAll);

    if (editor != null && editor.getSettings().isVariableInplaceRenameEnabled() &&
        (expr == null || expr.isPhysical()) && activeIntroducer == null) {
      myInplaceIntroduceFieldPopup =
        new InplaceIntroduceFieldPopup(localVariable, parentClass,
                                       parameters.declareStatic(),
                                       parameters.currentMethodConstructor(),
                                       occurrences, expr,
                                       new TypeSelectorManagerImpl(project, type, parameters.containingMethod(), expr, occurrences), editor,
                                       parameters.allowInitInMethod(), parameters.allowInitInMethodIfAll(), anchorElement,
                                       anchorElementIfAll, project);
      if (myInplaceIntroduceFieldPopup.startInplaceIntroduceTemplate()) {
        return null;
      }
    }

    IntroduceFieldDialog dialog = new IntroduceFieldDialog(
      project, parentClass, expr, localVariable,
      parameters.currentMethodConstructor(),
      localVariable != null, parameters.declareStatic(), occurrences,
      parameters.allowInitInMethod(), parameters.allowInitInMethodIfAll(),
      new TypeSelectorManagerImpl(project, type, parameters.containingMethod(), expr, occurrences),
      enteredName
    );
    dialog.setReplaceAllOccurrences(replaceAll);
    if (!dialog.showAndGet()) {
      if (parameters.occurrencesNumber() > 1) {
        WindowManager.getInstance().getStatusBar(project).setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"));
      }
      return null;
    }

    if (!dialog.isDeleteVariable()) {
      localVariable = null;
    }


    return new Settings(dialog.getEnteredName(), expr, occurrences, dialog.isReplaceAllOccurrences(),
                        parameters.declareStatic(), dialog.isDeclareFinal(),
                        dialog.getInitializerPlace(), dialog.getFieldVisibility(),
                        localVariable,
                        dialog.getFieldType(), localVariable != null, (TargetDestination)null, false, false);
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

  private record SettingParameters(@Nullable PsiMethod containingMethod,
                                   boolean declareStatic,
                                   int occurrencesNumber,
                                   boolean currentMethodConstructor,
                                   boolean allowInitInMethod,
                                   boolean allowInitInMethodIfAll) {
  }

  @Override
  protected boolean accept(ElementToWorkOn elementToWorkOn) {
    return true;
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

  @Override
  public AbstractInplaceIntroducer getInplaceIntroducer() {
    return myInplaceIntroduceFieldPopup;
  }

  static boolean isInSuperOrThis(@NotNull PsiExpression occurrence) {
    return !NotInConstructorCallFilter.INSTANCE.isOK(occurrence);
  }

  @Override
  public PsiField run(@NotNull PsiElement element, @NotNull JavaIntroduceFieldHandlerBase.InitializationPlace place) {
    if (!(element instanceof PsiExpression expression)) {
      return null;
    }
    ExpressionToFieldContext context = getContext(expression);
    if (!(context instanceof ExpressionToFieldContext.Success successContext)) {
      return null;
    }
    final OccurrenceManager occurrenceManager = createOccurrenceManager(successContext.selectedExpr(), successContext.parentClass());
    final PsiExpression[] occurrences = occurrenceManager.getOccurrences();
    final PsiElement anchorStatementIfAll = occurrenceManager.getAnchorStatementForAll();

    PsiElement tempAnchorElement = CommonJavaRefactoringUtil.getParentExpressionAnchorElement(successContext.selectedExpr());
    if (tempAnchorElement == null) {
      tempAnchorElement = successContext.selectedExpr();
    }
    SettingParameters parameters = getParameters(successContext.parentClass(), successContext.selectedExpr(),
                                                 occurrences, tempAnchorElement, anchorStatementIfAll);

    String visibility = JavaRefactoringSettings.getInstance().INTRODUCE_FIELD_VISIBILITY;
    if (visibility == null) {
      visibility = PsiModifier.PRIVATE;
    }
    SuggestedNameInfo suggestedName = JavaNameSuggestionUtil.suggestFieldName(
        successContext.tempType(), null, successContext.selectedExpr(),
        parameters.declareStatic(), successContext.parentClass());
    String name = suggestedName.names.length > 0 ? suggestedName.names[0] : "myField";

    boolean declareFinal = place == InitializationPlace.IN_FIELD_DECLARATION ||
                           (place == InitializationPlace.IN_CONSTRUCTOR && !parameters.declareStatic()) ||
                           (place == InitializationPlace.IN_CURRENT_METHOD &&
                            parameters.currentMethodConstructor() &&
                           successContext.parentClass().getConstructors().length <= 1);
    Settings settings = new Settings(name,
                                     successContext.selectedExpr(),
                                     occurrences,
                                     false,
                                     parameters.declareStatic,
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

    final ConvertToFieldRunnable runnable =
      new ConvertToFieldRunnable(successContext.selectedExpr(), settings, successContext.tempType(), occurrences,
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

  @Override
  public @NotNull JavaIntroduceFieldHandlerBase.AvailableSettings getAvailableSettings(@NotNull JavaIntroduceFieldHandlerBase.ExpressionToFieldContext.Success context) {
    ArrayList<InitializationPlace> places = new ArrayList<>(List.of(InitializationPlace.values()));
    final OccurrenceManager occurrenceManager = createOccurrenceManager(context.selectedExpr(), context.parentClass());
    final PsiExpression[] occurrences = occurrenceManager.getOccurrences();
    final PsiElement anchorStatementIfAll = occurrenceManager.getAnchorStatementForAll();
    PsiElement tempAnchorElement = CommonJavaRefactoringUtil.getParentExpressionAnchorElement(context.selectedExpr());
    SettingParameters parameters = getParameters(context.parentClass(), context.selectedExpr(), occurrences,
                                                 tempAnchorElement == null ? context.selectedExpr() : tempAnchorElement,
                                                 anchorStatementIfAll);

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

    if (!parameters.allowInitInMethod) {
      places.remove(InitializationPlace.IN_CURRENT_METHOD);
    }
    boolean inOnlyConstructor = parameters.currentMethodConstructor() && context.parentClass().getConstructors().length == 1;
    if (parameters.declareStatic() || inOnlyConstructor) {
      places.remove(InitializationPlace.IN_CONSTRUCTOR);
    }

    return new AvailableSettings(places);
  }


  @Override
  protected OccurrenceManager createOccurrenceManager(final PsiExpression selectedExpr, final PsiClass parentClass) {
    final OccurrenceFilter occurrenceFilter = isInSuperOrThis(selectedExpr) ? null : NotInConstructorCallFilter.INSTANCE;
    return new ExpressionOccurrenceManager(selectedExpr, parentClass, occurrenceFilter, true);
  }

  @Override
  protected boolean invokeImpl(final Project project, PsiLocalVariable localVariable, final Editor editor) {
    final PsiElement parent = localVariable.getParent();
    if (!(parent instanceof PsiDeclarationStatement)) {
      showErrorMessage(project, editor, JavaRefactoringBundle.message("error.wrong.caret.position.local.or.expression.name"));
      return false;
    }
    LocalToFieldHandler localToFieldHandler = new LocalToFieldHandler(project, false) {
      @Override
      protected Settings showRefactoringDialog(PsiClass aClass,
                                               PsiLocalVariable local,
                                               PsiExpression[] occurrences,
                                               boolean isStatic) {
        final PsiStatement statement = PsiTreeUtil.getParentOfType(local, PsiStatement.class);
        PsiType type = PsiTypesUtil.removeExternalAnnotations(local.getType());
        return IntroduceFieldHandler.this.showRefactoringDialog(project, editor, aClass, local.getInitializer(), type, occurrences, local,
                                                                statement);
      }

      @Override
      protected int getChosenClassIndex(List<PsiClass> classes) {
        return IntroduceFieldHandler.this.getChosenClassIndex(classes);
      }
    };
    return localToFieldHandler.convertLocalToField(localVariable, editor);
  }

  protected int getChosenClassIndex(List<PsiClass> classes) {
    return classes.size() - 1;
  }

  public static @NlsContexts.DialogTitle String getRefactoringNameText() {
    return RefactoringBundle.message("introduce.field.title");
  }
}
