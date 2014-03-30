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
package com.intellij.refactoring.wrapreturnvalue;

import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ScrollingModel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactorJBundle;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.NotNull;

class WrapReturnValueHandler implements RefactoringActionHandler {
    public static final String REFACTORING_NAME = RefactorJBundle.message("wrap.return.value");

    public void invoke(@NotNull Project project,
                       Editor editor,
                       PsiFile file,
                       DataContext dataContext){
        final ScrollingModel scrollingModel = editor.getScrollingModel();
        scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE);
        final PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
        PsiMethod selectedMethod = null;
        if(element instanceof PsiMethod){
            selectedMethod = (PsiMethod) element;
        } else{
            final CaretModel caretModel = editor.getCaretModel();
            final int position = caretModel.getOffset();
            PsiElement selectedElement = file.findElementAt(position);
            while(selectedElement != null){
                if(selectedElement instanceof PsiMethod){
                    selectedMethod = (PsiMethod) selectedElement;
                    break;
                }
                selectedElement = selectedElement.getParent();
            }
        }
        if(selectedMethod == null){
          CommonRefactoringUtil.showErrorHint(project, editor, RefactorJBundle.message("cannot.perform.the.refactoring") + RefactorJBundle.message(
              "the.caret.should.be.positioned.at.the.name.of.the.method.to.be.refactored"), null, this.getHelpID());
          return;
        }
      invoke(project, selectedMethod, editor);
    }

    protected String getRefactoringName(){
        return REFACTORING_NAME;
    }

    protected String getHelpID(){
        return HelpID.WrapReturnValue;
    }

    public void invoke(@NotNull Project project,
                       @NotNull PsiElement[] elements,
                       DataContext dataContext){
        if(elements.length != 1){
            return;
        }
        PsiMethod method =
                PsiTreeUtil.getParentOfType(elements[0], PsiMethod.class, false);
        if(method == null){
            return;
        }
      Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
      invoke(project, method, editor);
    }

  private void invoke(final Project project, PsiMethod method, Editor editor) {
    if(method.isConstructor()){
      CommonRefactoringUtil.showErrorHint(project, editor, RefactorJBundle.message("cannot.perform.the.refactoring") + RefactorJBundle.message("constructor.returns.can.not.be.wrapped"), null,
                                          this.getHelpID());
      return;
    }
    final PsiType returnType = method.getReturnType();
    if(PsiType.VOID.equals(returnType)){
      CommonRefactoringUtil.showErrorHint(project, editor, RefactorJBundle.message("cannot.perform.the.refactoring") + RefactorJBundle.message("method.selected.returns.void"), null, this.getHelpID());
      return;
    }
    method = SuperMethodWarningUtil.checkSuperMethod(method, RefactoringBundle.message("to.refactor"));
    if (method == null) return;

    if(method instanceof PsiCompiledElement){
      CommonRefactoringUtil.showErrorHint(project, editor, RefactorJBundle.message("cannot.perform.the.refactoring") + RefactorJBundle.message(
          "the.selected.method.cannot.be.wrapped.because.it.is.defined.in.a.non.project.class"), null, this.getHelpID());
      return;
    }

    new WrapReturnValueDialog(method).show();


  }



}
