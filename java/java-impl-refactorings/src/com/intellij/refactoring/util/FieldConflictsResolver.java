// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 *  Resolves conflicts with fields in a class, when new local variable is
 *  introduced in code block
 */
public class FieldConflictsResolver {
  private static final Logger LOG = Logger.getInstance(FieldConflictsResolver.class);
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
      if (reference.getElement() instanceof PsiReferenceExpression ref && ref.getQualifierExpression() == null) {
        myReferenceExpressions.add(ref);
      }
    }
    if (myField.hasModifierProperty(PsiModifier.STATIC)) {
      myQualifyingClass = myField.getContainingClass();
    }
  }

  public @NotNull PsiExpression fixInitializer(@NotNull PsiExpression initializer) {
    if (myField == null) return initializer;
    final PsiReferenceExpression[] replacedRef = {null};
    initializer.accept(new JavaRecursiveElementVisitor() {
      @Override
      public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
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
