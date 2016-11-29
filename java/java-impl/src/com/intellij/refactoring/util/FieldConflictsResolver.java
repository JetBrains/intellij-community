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
package com.intellij.refactoring.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;
import java.util.List;

/**
 *  Resolves conflicts with fields in a class, when new local variable is
 *  introduced in code block
 *  @author dsl
 */
public class FieldConflictsResolver {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.util.FieldConflictsResolver");
  private final PsiElement myScope;
  private final PsiField myField;
  private final List<PsiReferenceExpression> myReferenceExpressions;
  private PsiClass myQualifyingClass;

  public FieldConflictsResolver(String name, PsiCodeBlock scope) {
    this(name, (PsiElement)scope);
  }

  public FieldConflictsResolver(String name, PsiElement scope) {
    myScope = scope;
    if (myScope == null) {
      myField = null;
      myReferenceExpressions = null;
      return;
    }
    JavaPsiFacade facade = JavaPsiFacade.getInstance(myScope.getProject());
    final PsiVariable oldVariable = facade.getResolveHelper().resolveAccessibleReferencedVariable(name, myScope);
    myField = oldVariable instanceof PsiField ? (PsiField) oldVariable : null;
    if (!(oldVariable instanceof PsiField)) {
      myReferenceExpressions = null;
      return;
    }
    myReferenceExpressions = new ArrayList<>();
    for (PsiReference reference : ReferencesSearch.search(myField, new LocalSearchScope(myScope), false)) {
      final PsiElement element = reference.getElement();
      if (element instanceof PsiReferenceExpression) {
        final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)element;
        if (referenceExpression.getQualifierExpression() == null) {
          myReferenceExpressions.add(referenceExpression);
        }
      }
    }
    if (myField.hasModifierProperty(PsiModifier.STATIC)) {
      myQualifyingClass = myField.getContainingClass();
    }
  }

  public PsiExpression fixInitializer(PsiExpression initializer) {
    if (myField == null) return initializer;
    final PsiReferenceExpression[] replacedRef = {null};
    initializer.accept(new JavaRecursiveElementVisitor() {
      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        final PsiExpression qualifierExpression = expression.getQualifierExpression();
        if (qualifierExpression != null) {
          qualifierExpression.accept(this);
        }
        else {
          final PsiElement result = expression.resolve();
          if (expression.getManager().areElementsEquivalent(result, myField)) {
            try {
              replacedRef[0] = RefactoringChangeUtil.qualifyReference(expression, myField, myQualifyingClass);
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        }
      }
    });
    if (!initializer.isValid()) return replacedRef[0];
    return initializer;
  }

  public void fix() throws IncorrectOperationException {
    if (myField == null) return;
    final PsiManager manager = myScope.getManager();
    for (PsiReferenceExpression referenceExpression : myReferenceExpressions) {
      if (!referenceExpression.isValid()) continue;
      final PsiElement newlyResolved = referenceExpression.resolve();
      if (!manager.areElementsEquivalent(newlyResolved, myField)) {
        RefactoringChangeUtil.qualifyReference(referenceExpression, myField, myQualifyingClass);
      }
    }
  }
}
