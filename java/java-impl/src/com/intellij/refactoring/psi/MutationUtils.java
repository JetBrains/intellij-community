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
package com.intellij.refactoring.psi;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.IncorrectOperationException;

public class MutationUtils {
    private MutationUtils() {
        super();
    }


  public static void replaceType(String newExpression,
                                   PsiTypeElement typeElement)
            throws IncorrectOperationException {
        final PsiManager mgr = typeElement.getManager();
        final PsiElementFactory factory = JavaPsiFacade.getInstance(mgr.getProject()).getElementFactory();
        final PsiType newType =
                factory.createTypeFromText(newExpression, null);
        final PsiTypeElement newTypeElement = factory.createTypeElement(newType);
        final PsiElement insertedElement = typeElement.replace(newTypeElement);
    final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(mgr.getProject());
        final PsiElement shortenedElement = JavaCodeStyleManager.getInstance(mgr.getProject()).shortenClassReferences(insertedElement);
        codeStyleManager.reformat(shortenedElement);
    }

    public static void replaceExpression(String newExpression,
                                         PsiExpression exp)
            throws IncorrectOperationException {
        final PsiManager mgr = exp.getManager();
        final PsiElementFactory factory = JavaPsiFacade.getInstance(mgr.getProject()).getElementFactory();
        final PsiExpression newCall =
                factory.createExpressionFromText(newExpression, null);
        final PsiElement insertedElement = exp.replace(newCall);
      final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(mgr.getProject());
        final PsiElement shortenedElement = JavaCodeStyleManager.getInstance(mgr.getProject()).shortenClassReferences(insertedElement);
        codeStyleManager.reformat(shortenedElement);
    }

    public static void replaceExpressionIfValid(String newExpression,
                                         PsiExpression exp) throws IncorrectOperationException{
        final PsiManager mgr = exp.getManager();
        final PsiElementFactory factory = JavaPsiFacade.getInstance(mgr.getProject()).getElementFactory();
        final PsiExpression newCall;
        try{
            newCall = factory.createExpressionFromText(newExpression, null);
        } catch(IncorrectOperationException e){
            return;
        }
        final PsiElement insertedElement = exp.replace(newCall);
      final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(mgr.getProject());
        final PsiElement shortenedElement =JavaCodeStyleManager.getInstance(mgr.getProject()).shortenClassReferences(insertedElement);
        codeStyleManager.reformat(shortenedElement);
    }

  public static void replaceReference(String className,
                                      PsiJavaCodeReferenceElement reference)
    throws IncorrectOperationException {
    final PsiManager mgr = reference.getManager();
    final Project project = mgr.getProject();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(mgr.getProject());
    final PsiElementFactory factory = facade.getElementFactory();
    final GlobalSearchScope scope = GlobalSearchScope.allScope(project);


    final PsiElement insertedElement;
    final PsiElement parent = reference.getParent();
    if (parent instanceof PsiReferenceExpression) {
      final PsiClass aClass = facade.findClass(className, scope);
      if (aClass == null) return;
      ((PsiReferenceExpression)parent).setQualifierExpression(factory.createReferenceExpression(aClass));
      insertedElement = ((PsiReferenceExpression)parent).getQualifierExpression();
    }
    else {
      final PsiJavaCodeReferenceElement newReference =
        factory.createReferenceElementByFQClassName(className, scope);
      insertedElement = reference.replace(newReference);
    }
    final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(mgr.getProject());
    final PsiElement shortenedElement = JavaCodeStyleManager.getInstance(mgr.getProject()).shortenClassReferences(insertedElement);
    codeStyleManager.reformat(shortenedElement);
  }

    public static void replaceStatement(String newStatement,
                                        PsiStatement statement)
            throws IncorrectOperationException {
        final Project project = statement.getProject();
        final PsiManager mgr = PsiManager.getInstance(project);
        final PsiElementFactory factory = JavaPsiFacade.getInstance(mgr.getProject()).getElementFactory();
        final PsiStatement newCall =
                factory.createStatementFromText(newStatement, null);
        final PsiElement insertedElement = statement.replace(newCall);
      final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(mgr.getProject());
        final PsiElement shortenedElement = JavaCodeStyleManager.getInstance(mgr.getProject()).shortenClassReferences(insertedElement);
        codeStyleManager.reformat(shortenedElement);
    }

}
