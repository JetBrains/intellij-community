// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.introduceField;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.AbstractJavaInplaceIntroducer;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.introduce.inplace.AbstractInplaceIntroducer;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.refactoring.util.occurrences.ExpressionOccurrenceManager;
import com.intellij.refactoring.util.occurrences.NotInConstructorCallFilter;
import com.intellij.refactoring.util.occurrences.OccurrenceFilter;
import com.intellij.refactoring.util.occurrences.OccurrenceManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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
  protected boolean validClass(PsiClass parentClass, PsiExpression selectedExpr, Editor editor) {
    return canIntroduceField(parentClass, selectedExpr.getType(), editor);
  }

  /**
   * Checks if a field of the specified type can be created in the specified class.
   *
   * @param parentClass  the class to create a field in
   * @param type  the type of the field that should be created
   * @param editor  to show error message for, if a problem is found
   * @return true, if a field can be introduced. false, if there is a problem.
   */
  static boolean canIntroduceField(@NotNull PsiClass parentClass, @Nullable PsiType type, Editor editor) {
    if (parentClass.isInterface()) {
      String message = JavaRefactoringBundle.message("cannot.introduce.field.in.interface");
      showErrorMessage(parentClass.getProject(), editor, message);
      return false;
    }
    PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(type);
    if (aClass != null && PsiUtil.isLocalClass(aClass) && !PsiTreeUtil.isAncestor(aClass, parentClass, false)) {
      String message = JavaRefactoringBundle.message("0.is.not.visible.to.members.of.1",
                                                     RefactoringUIUtil.getDescription(aClass, false),
                                                     RefactoringUIUtil.getDescription(parentClass, false));
      showErrorMessage(aClass.getProject(), editor, StringUtil.capitalize(message));
      return false;
    }
    return true;
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
  public void invoke(@NotNull final Project project, final Editor editor, PsiFile file, DataContext dataContext) {
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, file)) return;
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    ElementToWorkOn.processElementToWorkOn(editor, file, getRefactoringNameText(), HelpID.INTRODUCE_FIELD, project, getElementProcessor(project, editor));
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

    if (editor != null && editor.getSettings().isVariableInplaceRenameEnabled() &&
        (expr == null || expr.isPhysical()) && activeIntroducer == null) {
      myInplaceIntroduceFieldPopup =
        new InplaceIntroduceFieldPopup(localVariable, parentClass, declareStatic, currentMethodConstructor, occurrences, expr,
                                       new TypeSelectorManagerImpl(project, type, containingMethod, expr, occurrences), editor,
                                       allowInitInMethod, allowInitInMethodIfAll, anchorElement, anchorElementIfAll, project);
      if (myInplaceIntroduceFieldPopup.startInplaceIntroduceTemplate()) {
        return null;
      }
    }

    IntroduceFieldDialog dialog = new IntroduceFieldDialog(
      project, parentClass, expr, localVariable,
      currentMethodConstructor,
      localVariable != null, declareStatic, occurrences,
      allowInitInMethod, allowInitInMethodIfAll,
      new TypeSelectorManagerImpl(project, type, containingMethod, expr, occurrences),
      enteredName
    );
    dialog.setReplaceAllOccurrences(replaceAll);
    if (!dialog.showAndGet()) {
      if (occurrencesNumber > 1) {
        WindowManager.getInstance().getStatusBar(project).setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"));
      }
      return null;
    }

    if (!dialog.isDeleteVariable()) {
      localVariable = null;
    }


    return new Settings(dialog.getEnteredName(), expr, occurrences, dialog.isReplaceAllOccurrences(),
                        declareStatic, dialog.isDeclareFinal(),
                        dialog.getInitializerPlace(), dialog.getFieldVisibility(),
                        localVariable,
                        dialog.getFieldType(), localVariable != null, (TargetDestination)null, false, false);
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

  static boolean isInSuperOrThis(PsiExpression occurrence) {
    return !NotInConstructorCallFilter.INSTANCE.isOK(occurrence);
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
    LocalToFieldHandler localToFieldHandler = new LocalToFieldHandler(project, false){
      @Override
      protected Settings showRefactoringDialog(PsiClass aClass,
                                               PsiLocalVariable local,
                                               PsiExpression[] occurrences,
                                               boolean isStatic) {
        final PsiStatement statement = PsiTreeUtil.getParentOfType(local, PsiStatement.class);
        return IntroduceFieldHandler.this.showRefactoringDialog(project, editor, aClass, local.getInitializer(), local.getType(), occurrences, local, statement);
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
