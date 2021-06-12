/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.refactoring.makeStatic;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.lang.ContextAwareActionHandler;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.actions.BaseRefactoringAction;
import com.intellij.refactoring.actions.RefactoringActionContextUtil;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MakeStaticHandler implements RefactoringActionHandler, ContextAwareActionHandler {
  private static final Logger LOG = Logger.getInstance(MakeStaticHandler.class);

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    if (element == null) {
      element = file.findElementAt(editor.getCaretModel().getOffset());
    }

    if (element == null) return;
    if (element instanceof PsiIdentifier) element = element.getParent();

    if(!(element instanceof PsiTypeParameterListOwner)) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("error.wrong.caret.position.method.or.class.name"));
      CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HelpID.MAKE_METHOD_STATIC);
      return;
    }
    if(LOG.isDebugEnabled()) {
      LOG.debug("MakeStaticHandler invoked");
    }
    invoke(project, new PsiElement[]{element}, dataContext);
  }

  @Override
  public void invoke(@NotNull final Project project, PsiElement @NotNull [] elements, DataContext dataContext) {
    if(elements.length != 1 || !(elements[0] instanceof PsiTypeParameterListOwner)) return;

    final PsiTypeParameterListOwner member = (PsiTypeParameterListOwner)elements[0];
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, member)) return;

    String error = validateTarget(member);
    if (error != null) {
      Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
      CommonRefactoringUtil.showErrorHint(project, editor, error, getRefactoringName(), HelpID.MAKE_METHOD_STATIC);
      return;
    }

    invoke(member);
  }

  @Override
  public boolean isAvailableForQuickList(@NotNull Editor editor, @NotNull PsiFile file, @NotNull DataContext dataContext) {
    PsiElement element = BaseRefactoringAction.getElementAtCaret(editor, file);
    return RefactoringActionContextUtil.getJavaMethodHeader(element) != null || RefactoringActionContextUtil.isJavaClassHeader(element);
  }

  public static void invoke(final PsiTypeParameterListOwner member) {
    final Project project = member.getProject();
    final InternalUsageInfo[] classRefsInMember = MakeStaticUtil.findClassRefsInMember(member, false);

    /*
    String classParameterName = "anObject";
    ParameterTablePanel.VariableData[] fieldParameterData = null;

    */
    AbstractMakeStaticDialog dialog;
    if (!ApplicationManager.getApplication().isUnitTestMode()) {

      final boolean[] hasMethodReferenceOnInstance = new boolean[] {false};
      if (member instanceof PsiMethod) {
        if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(
          (Runnable)() -> hasMethodReferenceOnInstance[0] = !MethodReferencesSearch.search((PsiMethod)member).forEach(reference -> {
            final PsiElement element = reference.getElement();
            return !(element instanceof PsiMethodReferenceExpression);
          }), JavaRefactoringBundle.message("make.static.method.references.progress"), true, project)) return;
      }

      if (classRefsInMember.length > 0 || hasMethodReferenceOnInstance[0]) {
        final PsiType type = JavaPsiFacade.getElementFactory(project).createType(member.getContainingClass());
        //TODO: callback
        String[] nameSuggestions =
                JavaCodeStyleManager.getInstance(project).suggestVariableName(VariableKind.PARAMETER, null, null, type).names;

        dialog = new MakeParameterizedStaticDialog(project, member,
                                                   nameSuggestions,
                                                   classRefsInMember);


      }
      else {
        dialog = new SimpleMakeStaticDialog(project, member);
      }

      dialog.show();
    }
  }

  @Nullable
  public static @NlsContexts.DialogMessage String validateTarget(final PsiTypeParameterListOwner member) {
    final PsiClass containingClass = member.getContainingClass();

    // Checking various preconditions
    if(member instanceof PsiMethod && ((PsiMethod)member).isConstructor()) {
      return RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("constructor.cannot.be.made.static"));
    }

    if(member.getContainingClass() == null) {
      return RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("this.member.does.not.seem.to.belong.to.any.class"));
    }

    if(member.hasModifierProperty(PsiModifier.STATIC)) {
      return RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("member.is.already.static"));
    }

    if(member instanceof PsiMethod && member.hasModifierProperty(PsiModifier.ABSTRACT)) {
      return RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("cannot.make.abstract.method.static"));
    }

    if(containingClass instanceof PsiAnonymousClass || 
       (containingClass.getContainingClass() != null && !containingClass.hasModifierProperty(PsiModifier.STATIC))) {
      return RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("inner.classes.cannot.have.static.members"));
    }
    return null;
  }

  public static @NlsContexts.DialogTitle String getRefactoringName() {
    return JavaRefactoringBundle.message("make.method.static.title");
  }
}
