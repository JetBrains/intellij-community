/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.refactoring.introduceField;

import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.introduce.inplace.AbstractInplaceIntroducer;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.occurrences.ExpressionOccurrenceManager;
import com.intellij.refactoring.util.occurrences.OccurrenceManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class IntroduceConstantHandler extends BaseExpressionToFieldHandler {
  protected InplaceIntroduceConstantPopup myInplaceIntroduceConstantPopup;

  public IntroduceConstantHandler() {
    super(true);
  }

  @Override
  protected String getHelpID() {
    return HelpID.INTRODUCE_CONSTANT;
  }

  public void invoke(Project project, PsiExpression[] expressions) {
    for (PsiExpression expression : expressions) {
      final PsiFile file = expression.getContainingFile();
      if (!CommonRefactoringUtil.checkReadOnlyStatus(project, file)) return;
    }
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    super.invoke(project, expressions, null);
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, PsiFile file, DataContext dataContext) {
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, file)) return;

    PsiDocumentManager.getInstance(project).commitAllDocuments();
    ElementToWorkOn.processElementToWorkOn(editor, file, getRefactoringNameText(), getHelpID(), project, getElementProcessor(project, editor));
  }

  @Override
  protected boolean invokeImpl(final Project project, final PsiLocalVariable localVariable, final Editor editor) {
    final PsiElement parent = localVariable.getParent();
    if (!(parent instanceof PsiDeclarationStatement)) {
      String message = RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("error.wrong.caret.position.local.or.expression.name"));
      CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringNameText(), getHelpID());
      return false;
    }
    final LocalToFieldHandler localToFieldHandler = new LocalToFieldHandler(project, true){
      @Override
      protected Settings showRefactoringDialog(PsiClass aClass,
                                               PsiLocalVariable local,
                                               PsiExpression[] occurences,
                                               boolean isStatic) {
        return IntroduceConstantHandler.this.showRefactoringDialog(project, editor, aClass, local.getInitializer(), local.getType(), occurences, local, null);
      }
    };
    return localToFieldHandler.convertLocalToField(localVariable, editor);
  }


  @Override
  protected Settings showRefactoringDialog(Project project,
                                           final Editor editor,
                                           PsiClass parentClass,
                                           PsiExpression expr,
                                           PsiType type,
                                           PsiExpression[] occurrences,
                                           PsiElement anchorElement,
                                           PsiElement anchorElementIfAll) {
    final PsiMethod containingMethod = PsiTreeUtil.getParentOfType(expr != null ? expr : anchorElement, PsiMethod.class);

    ElementToWorkOn elementToWorkOn = ElementToWorkOn.adjustElements(expr, anchorElement);
    PsiLocalVariable localVariable = elementToWorkOn.getLocalVariable();
    expr = elementToWorkOn.getExpression();

    String enteredName = null;
    boolean replaceAllOccurrences = true;

    final AbstractInplaceIntroducer activeIntroducer = AbstractInplaceIntroducer.getActiveIntroducer(editor);
    if (activeIntroducer != null) {
      activeIntroducer.stopIntroduce(editor);
      expr = (PsiExpression)activeIntroducer.getExpr();
      localVariable = (PsiLocalVariable)activeIntroducer.getLocalVariable();
      occurrences = (PsiExpression[])activeIntroducer.getOccurrences();
      enteredName = activeIntroducer.getInputName();
      replaceAllOccurrences = activeIntroducer.isReplaceAllOccurrences();
      type = ((InplaceIntroduceConstantPopup)activeIntroducer).getType();
    }

    for (PsiExpression occurrence : occurrences) {
      if (RefactoringUtil.isAssignmentLHS(occurrence)) {
        String message =
          RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("introduce.constant.used.for.write.cannot.refactor.message"));
        CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringNameText(), getHelpID());
        highlightError(project, editor, occurrence);
        return null;
      }
    }

    if (localVariable == null) {
      final PsiElement errorElement = isStaticFinalInitializer(expr);
      if (errorElement != null) {
        String message =
          RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("selected.expression.cannot.be.a.constant.initializer"));
        CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringNameText(), getHelpID());
        highlightError(project, editor, errorElement);
        return null;
      }
    }
    else {
      final PsiExpression initializer = localVariable.getInitializer();
      if (initializer == null) {
        String message = RefactoringBundle
          .getCannotRefactorMessage(JavaRefactoringBundle.message("variable.does.not.have.an.initializer", localVariable.getName()));
        CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringNameText(), getHelpID());
        return null;
      }
      final PsiElement errorElement = isStaticFinalInitializer(initializer);
      if (errorElement != null) {
        String message = RefactoringBundle.getCannotRefactorMessage(
          JavaRefactoringBundle.message("initializer.for.variable.cannot.be.a.constant.initializer", localVariable.getName()));
        CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringNameText(), getHelpID());
        highlightError(project, editor, errorElement);
        return null;
      }
    }


    final TypeSelectorManagerImpl typeSelectorManager = new TypeSelectorManagerImpl(project, type, containingMethod, expr, occurrences);
    if (editor != null && editor.getSettings().isVariableInplaceRenameEnabled() &&
        (expr == null || expr.isPhysical()) && activeIntroducer == null) {
      myInplaceIntroduceConstantPopup =
        new InplaceIntroduceConstantPopup(project, editor, parentClass, expr, localVariable, occurrences,
                                          typeSelectorManager,
                                          anchorElement, anchorElementIfAll,
                                          expr != null ? createOccurrenceManager(expr, parentClass) : null);
      if (myInplaceIntroduceConstantPopup.startInplaceIntroduceTemplate()) {
        return null;
      }
    }


    final IntroduceConstantDialog dialog =
      new IntroduceConstantDialog(project, parentClass, expr, localVariable, localVariable != null, occurrences, getParentClass(),
                                  typeSelectorManager, enteredName);
    dialog.setReplaceAllOccurrences(replaceAllOccurrences);
    if (!dialog.showAndGet()) {
      if (occurrences.length > 1) {
        WindowManager.getInstance().getStatusBar(project).setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"));
      }
      return null;
    }
    return new Settings(dialog.getEnteredName(), expr, occurrences, dialog.isReplaceAllOccurrences(), true, true,
                        InitializationPlace.IN_FIELD_DECLARATION, dialog.getFieldVisibility(), localVariable,
                        dialog.getSelectedType(), dialog.isDeleteVariable(), dialog.getDestinationClass(),
                        dialog.isAnnotateAsNonNls(),
                        dialog.introduceEnumConstant());
  }

  private static void highlightError(Project project, Editor editor, PsiElement errorElement) {
    if (editor != null) {
      final TextRange textRange = errorElement.getTextRange();
      HighlightManager.getInstance(project).addRangeHighlight(editor, textRange.getStartOffset(), textRange.getEndOffset(),
                                                              EditorColors.SEARCH_RESULT_ATTRIBUTES, true, new ArrayList<>());
    }
  }

  @Override
  protected String getRefactoringName() {
    return getRefactoringNameText();
  }

  @Override
  public AbstractInplaceIntroducer getInplaceIntroducer() {
    return myInplaceIntroduceConstantPopup;
  }

  @Override
  protected OccurrenceManager createOccurrenceManager(final PsiExpression selectedExpr, final PsiClass parentClass) {
    return new ExpressionOccurrenceManager(selectedExpr, parentClass, null);
  }

  @Override
  public PsiClass getParentClass(@NotNull PsiExpression initializerExpression) {
    final PsiType type = initializerExpression.getType();

    if (PsiUtil.isConstantExpression(initializerExpression) &&
        (type instanceof PsiPrimitiveType || type != null && type.equalsToText(CommonClassNames.JAVA_LANG_STRING))) {
      return super.getParentClass(initializerExpression);
    }

    PsiElement parent = initializerExpression.getUserData(ElementToWorkOn.PARENT);
    if (parent == null) parent = initializerExpression;
    PsiClass aClass = PsiTreeUtil.getParentOfType(parent, PsiClass.class);
    while (aClass != null) {
      if (LocalToFieldHandler.mayContainConstants(aClass)) return aClass;
      aClass = PsiTreeUtil.getParentOfType(aClass, PsiClass.class);
    }
    return null;
  }

  @Override
  protected boolean accept(ElementToWorkOn elementToWorkOn) {
    final PsiExpression expr = elementToWorkOn.getExpression();
    if (expr != null) {
      return isStaticFinalInitializer(expr) == null;
    }
    final PsiLocalVariable localVariable = elementToWorkOn.getLocalVariable();
    final PsiExpression initializer = localVariable.getInitializer();
    return initializer != null && isStaticFinalInitializer(initializer) == null;
  }

  @Override
  protected boolean validClass(PsiClass parentClass, Editor editor) {
    return true;
  }

  public static @NlsActions.ActionText String getRefactoringNameText() {
    return RefactoringBundle.message("introduce.constant.title");
  }
}
