/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.occurences.*;
import org.jetbrains.annotations.NotNull;

public class IntroduceFieldHandler extends BaseExpressionToFieldHandler {

  public static final String REFACTORING_NAME = RefactoringBundle.message("introduce.field.title");
  private static final MyOccurenceFilter MY_OCCURENCE_FILTER = new MyOccurenceFilter();

  protected String getRefactoringName() {
    return REFACTORING_NAME;
  }

  protected boolean validClass(PsiClass parentClass, Editor editor) {
    if (parentClass.isInterface()) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("cannot.introduce.field.in.interface"));
      CommonRefactoringUtil.showErrorHint(parentClass.getProject(), editor, message, REFACTORING_NAME, getHelpID());
      return false;
    }
    else {
      return true;
    }
  }

  protected String getHelpID() {
    return HelpID.INTRODUCE_FIELD;
  }

  public void invoke(@NotNull final Project project, final Editor editor, PsiFile file, DataContext dataContext) {
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, file)) return;
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    ElementToWorkOn.processElementToWorkOn(editor, file, REFACTORING_NAME, HelpID.INTRODUCE_FIELD, project, getElementProcessor(project, editor));
  }

  protected Settings showRefactoringDialog(Project project, Editor editor, PsiClass parentClass, PsiExpression expr,
                                           PsiType type,
                                           PsiExpression[] occurences, PsiElement anchorElement, PsiElement anchorElementIfAll) {
    final PsiMethod containingMethod = PsiTreeUtil.getParentOfType(expr != null ? expr : anchorElement, PsiMethod.class);
    PsiElement element = null;
    if (expr != null) {
      element = expr.getUserData(ElementToWorkOn.PARENT);
      if (element == null) element = expr;
    }
    if (element == null) element = anchorElement;
    final PsiModifierListOwner staticParentElement = PsiUtil.getEnclosingStaticElement(element, parentClass);
    boolean declareStatic = staticParentElement != null;

    boolean isInSuperOrThis = false;
    if (!declareStatic) {
      for (int i = 0; !declareStatic && i < occurences.length; i++) {
        PsiExpression occurence = occurences[i];
        isInSuperOrThis = isInSuperOrThis(occurence);
        declareStatic = isInSuperOrThis;
      }
    }

    PsiLocalVariable localVariable = null;
    if (expr instanceof PsiReferenceExpression) {
      PsiElement ref = ((PsiReferenceExpression)expr).resolve();
      if (ref instanceof PsiLocalVariable) {
        localVariable = (PsiLocalVariable)ref;
      }
    }

    int occurencesNumber = occurences.length;
    final boolean currentMethodConstructor = containingMethod != null && containingMethod.isConstructor();
    final boolean allowInitInMethod = (!currentMethodConstructor || !isInSuperOrThis) && anchorElement instanceof PsiStatement;
    final boolean allowInitInMethodIfAll = (!currentMethodConstructor || !isInSuperOrThis) && anchorElementIfAll instanceof PsiStatement;
    IntroduceFieldDialog dialog = new IntroduceFieldDialog(
      project, parentClass, expr, localVariable,
      currentMethodConstructor,
      false, declareStatic, occurencesNumber,
      allowInitInMethod, allowInitInMethodIfAll,
      new TypeSelectorManagerImpl(project, type, containingMethod, expr, occurences)
    );
    dialog.show();

    if (!dialog.isOK()) {
      if (occurencesNumber > 1) {
        WindowManager.getInstance().getStatusBar(project).setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"));
      }
      return null;
    }

    if (!dialog.isDeleteVariable()) {
      localVariable = null;
    }


    return new Settings(dialog.getEnteredName(), dialog.isReplaceAllOccurrences(),
                        declareStatic, dialog.isDeclareFinal(),
                        dialog.getInitializerPlace(), dialog.getFieldVisibility(),
                        localVariable,
                        dialog.getFieldType(), localVariable != null, (TargetDestination)null, false, false);
  }

  private static boolean isInSuperOrThis(PsiExpression occurence) {
    return !NotInSuperCallOccurenceFilter.INSTANCE.isOK(occurence) || !NotInThisCallFilter.INSTANCE.isOK(occurence);
  }

  protected OccurenceManager createOccurenceManager(final PsiExpression selectedExpr, final PsiClass parentClass) {
    final OccurenceFilter occurenceFilter = isInSuperOrThis(selectedExpr) ? null : MY_OCCURENCE_FILTER;
    return new ExpressionOccurenceManager(selectedExpr, parentClass, occurenceFilter, true);
  }

  protected boolean invokeImpl(final Project project, PsiLocalVariable localVariable, final Editor editor) {
    LocalToFieldHandler localToFieldHandler = new LocalToFieldHandler(project, false){
      @Override
      protected Settings showRefactoringDialog(PsiClass aClass,
                                               PsiLocalVariable local,
                                               PsiExpression[] occurences,
                                               boolean isStatic) {
        final PsiStatement statement = PsiTreeUtil.getParentOfType(local, PsiStatement.class);
        return IntroduceFieldHandler.this.showRefactoringDialog(project, editor, aClass, local.getInitializer(), local.getType(), occurences, statement, statement);
      }
    };
    return localToFieldHandler.convertLocalToField(localVariable, editor);
  }

  private static class MyOccurenceFilter implements OccurenceFilter {
    public boolean isOK(PsiExpression occurence) {
      return !isInSuperOrThis(occurence);
    }
  }
}
