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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.AssignFieldFromParameterAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.jspJava.JspClass;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Comparator;

public class CreateConstructorParameterFromFieldFix implements IntentionAction {
  private final SmartPsiElementPointer<PsiField> myField;

  public CreateConstructorParameterFromFieldFix(@NotNull PsiField field) {
    myField = SmartPointerManager.getInstance(field.getProject()).createSmartPsiElementPointer(field);
  }

  @NotNull
  public String getText() {
    return QuickFixBundle.message("add.constructor.parameter.name");
  }

  @NotNull
  public String getFamilyName() {
    return getText();
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    final PsiField field = getField();
    PsiClass containingClass = field == null ? null : field.getContainingClass();
    return
           field != null
           && field.getManager().isInProject(field)
           && !field.hasModifierProperty(PsiModifier.STATIC)
           && containingClass != null
           && !(containingClass instanceof JspClass)
           && containingClass.getName() != null
      ;
  }

  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    if (!CodeInsightUtilBase.prepareFileForWrite(file)) return;

    PsiClass aClass = getField().getContainingClass();
    PsiMethod[] constructors = aClass.getConstructors();
    if (constructors.length == 0) {
      final AddDefaultConstructorFix defaultConstructorFix = new AddDefaultConstructorFix(aClass);
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          defaultConstructorFix.invoke(project, editor, file);
        }
      });
      aClass = getField().getContainingClass();
      constructors = aClass.getConstructors();
    }
    Arrays.sort(constructors, new Comparator<PsiMethod>() {
      @Override
      public int compare(PsiMethod c1, PsiMethod c2) {
        final PsiMethod cc1 = RefactoringUtil.getChainedConstructor(c1);
        final PsiMethod cc2 = RefactoringUtil.getChainedConstructor(c2);
        if (cc1 == c2) return 1;
        if (cc2 == c1) return -1;
        if (cc1 == null) {
          return cc2 == null ? 0 : compare(c1, cc2);
        } else {
          return cc2 == null ? compare(cc1, c2) : compare(cc1, cc2);
        }
      }
    });
    for (PsiMethod constructor : constructors) {
      if (!addParameterToConstructor(project, file, editor, constructor)) break;
    }
  }

  private boolean addParameterToConstructor(final Project project, final PsiFile file, final Editor editor, PsiMethod constructor) throws IncorrectOperationException {
    final PsiParameter[] parameters = constructor.getParameterList().getParameters();
    PsiExpression[] expressions = new PsiExpression[parameters.length+1];
    PsiElementFactory factory = JavaPsiFacade.getInstance(file.getProject()).getElementFactory();
    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      String value = PsiTypesUtil.getDefaultValueOfType(parameter.getType());
      expressions[i] = factory.createExpressionFromText(value, parameter);
    }
    expressions[parameters.length] = factory.createExpressionFromText(getField().getName(), constructor);
    final SmartPointerManager manager = SmartPointerManager.getInstance(getField().getProject());
    final SmartPsiElementPointer constructorPointer = manager.createSmartPsiElementPointer(constructor);

    final ChangeMethodSignatureFromUsageFix addParamFix = new ChangeMethodSignatureFromUsageFix(constructor, expressions, PsiSubstitutor.EMPTY, constructor, true, 1);
    addParamFix.invoke(project, editor, file);
    return ApplicationManager.getApplication().runWriteAction(new Computable<Boolean>() {
      public Boolean compute() {
        return doCreate(project, editor, parameters, constructorPointer, addParamFix);
      }
    });
  }

  private boolean doCreate(Project project, Editor editor, PsiParameter[] parameters, SmartPsiElementPointer constructorPointer,
                           ChangeMethodSignatureFromUsageFix addParamFix) {
    PsiMethod constructor = (PsiMethod)constructorPointer.getElement();
    assert constructor != null;
    PsiParameter[] newParameters = constructor.getParameterList().getParameters();
    if (newParameters == parameters) return false; //user must have canceled dialog
    String newName = addParamFix.getNewParameterNameByOldIndex(-1);
    PsiParameter parameter = null;
    for (PsiParameter newParameter : newParameters) {
      if (Comparing.strEqual(newName, newParameter.getName())) {
        parameter = newParameter;
        break;
      }
    }
    if (parameter == null) return false;

    // do not introduce assignment in chanined constructor
    if (HighlightControlFlowUtil.getChainedConstructors(constructor) == null) {
      AssignFieldFromParameterAction.addFieldAssignmentStatement(project, getField(), parameter, editor);
    }
    return true;
  }

  private PsiField getField() {
    return myField.getElement();
  }

  public boolean startInWriteAction() {
    return false;
  }
}
