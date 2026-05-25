// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.introduceField;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.refactoring.AbstractJavaInplaceIntroducer;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.introduce.inplace.AbstractInplaceIntroducer;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class IntroduceFieldHandler extends BaseExpressionToFieldHandler implements JavaIntroduceFieldHandlerBase {
  private InplaceIntroduceFieldPopup myInplaceIntroduceFieldPopup;

  public IntroduceFieldHandler() {
    super(new IntroduceFieldHelper());
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
    String message = IntroduceFieldHelper.checkCanIntroduceField(parentClass, type);
    if (message != null) {
      showErrorMessage(parentClass.getProject(), editor, message);
      return false;
    }
    return true;
  }

  private static void showErrorMessage(@NotNull Project project, Editor editor, @NlsContexts.DialogMessage String message) {
    message = RefactoringBundle.getCannotRefactorMessage(message);
    CommonRefactoringUtil.showErrorHint(project, editor, message, IntroduceFieldHelper.getRefactoringNameText(), HelpID.INTRODUCE_FIELD);
  }

  @Override
  protected String getHelpID() {
    return HelpID.INTRODUCE_FIELD;
  }

  @Override
  public void invoke(final @NotNull Project project, final Editor editor, PsiFile file, DataContext dataContext) {
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, file)) return;
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    ElementToWorkOn.processElementToWorkOn(editor, file, IntroduceFieldHelper.getRefactoringNameText(), HelpID.INTRODUCE_FIELD, project,
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

    FieldExtractor.SettingParameters parameters =
      FieldExtractor.getParameters(parentClass, expr, occurrences, anchorElement, anchorElementIfAll, false);

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

  @Override
  public AbstractInplaceIntroducer getInplaceIntroducer() {
    return myInplaceIntroduceFieldPopup;
  }

  @Override
  protected boolean invokeImpl(final Project project, PsiLocalVariable localVariable, final Editor editor) {
    JavaIntroduceFieldService.ToFieldContext context = FieldExtractor.getContext(myHelper, localVariable, false);
    if (context instanceof JavaIntroduceFieldService.ToFieldContext.Error(String errorMessage)) {
      CommonRefactoringUtil.showErrorHint(project, editor, errorMessage, IntroduceFieldHelper.getRefactoringNameText(), getHelpID());
      return false;
    }
    final PsiElement parent = localVariable.getParent();
    if (!(parent instanceof PsiDeclarationStatement)) {
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
    return localToFieldHandler.convertLocalToField(localVariable, LocalToFieldHandler.getCandidatesContext(localVariable, false), editor);
  }

  protected int getChosenClassIndex(List<PsiClass> classes) {
    return classes.size() - 1;
  }
}
