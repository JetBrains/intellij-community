// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.util.classRefs;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CommonJavaRefactoringUtil;

public class ClassInstanceScanner extends DelegatingClassReferenceVisitor {
  private static final Logger LOG = Logger.getInstance(ClassInstanceScanner.class);
  private final PsiClass myClass;
  private final ClassInstanceReferenceVisitor myVisitor;

  public interface ClassInstanceReferenceVisitor {
    void visitQualifier(PsiReferenceExpression qualified, PsiExpression instanceRef, PsiElement referencedInstance);
    void visitTypeCast(PsiTypeCastExpression typeCastExpression, PsiExpression instanceRef, PsiElement referencedInstance);
    void visitReadUsage(PsiExpression instanceRef, PsiType expectedType, PsiElement referencedInstance);
    void visitWriteUsage(PsiExpression instanceRef, PsiType assignedType, PsiElement referencedInstance);
  }

  public ClassInstanceScanner(PsiClass aClass, ClassInstanceReferenceVisitor visitor) {
    this(aClass, ClassReferenceVisitorAdapter.INSTANCE, visitor);
  }

  public ClassInstanceScanner(PsiClass aClass, ClassReferenceVisitor delegate,
                              ClassInstanceReferenceVisitor visitor) {
    super(delegate);
    myClass = aClass;
    myVisitor = visitor;
  }

  @Override public void visitLocalVariableDeclaration(PsiLocalVariable variable, TypeOccurence occurence) {
    visitVariable(variable, occurence);
  }

  @Override public void visitFieldDeclaration(PsiField field, TypeOccurence occurence) {
    visitVariable(field, occurence);
  }

  @Override public void visitParameterDeclaration(PsiParameter parameter, TypeOccurence occurence) {
    visitVariable(parameter, occurence);
  }

  private void visitVariable(PsiVariable variable, TypeOccurence occurence) {
    GlobalSearchScope projectScope = GlobalSearchScope.projectScope(myClass.getProject());
    for (PsiReference reference : ReferencesSearch.search(variable, projectScope, false)) {
      PsiElement element = reference.getElement();

      // todo: handle arrays
      if (element instanceof PsiExpression) {
        processExpression((PsiExpression)element, occurence, variable);
      }
      else {
        // todo: java doc processing?
//        LOG.assertTrue(false);
      }
    }
  }

  @Override public void visitMethodReturnType(PsiMethod method, TypeOccurence occurence) {
    GlobalSearchScope projectScope = GlobalSearchScope.projectScope(myClass.getProject());

    for (PsiReference ref : ReferencesSearch.search(method, projectScope, false)) {
      PsiElement element = ref.getElement();
      if (element instanceof PsiReferenceExpression) {
        PsiElement parent = element.getParent();
        if (parent instanceof PsiMethodCallExpression) {
          processExpression((PsiMethodCallExpression)parent, occurence, method);
        }
      }
    }
  }

  @Override public void visitTypeCastExpression(PsiTypeCastExpression typeCastExpression, TypeOccurence occurence) {
    processExpression(typeCastExpression, occurence, null);
  }

  @Override public void visitNewExpression(PsiNewExpression newExpression, TypeOccurence occurence) {
    processExpression(newExpression, occurence, null);
  }

  private void processExpression(PsiExpression expression, TypeOccurence occurence, PsiElement referencedElement) {
    if(!(occurence.outermostType instanceof PsiArrayType)) {
      processNonArrayExpression(myVisitor, expression, referencedElement);
    }
    else {
      PsiType type = occurence.outermostType;
      PsiExpression result = CommonJavaRefactoringUtil.outermostParenthesizedExpression(expression);
      while(type instanceof PsiArrayType && result.getParent() instanceof PsiArrayAccessExpression) {
        type = ((PsiArrayType) type).getComponentType();
        result = CommonJavaRefactoringUtil.outermostParenthesizedExpression((PsiArrayAccessExpression) result.getParent());
      }
      if(!(type instanceof PsiArrayType)) {
        processNonArrayExpression(myVisitor, result, referencedElement);
      }
    }
  }

  public static void processNonArrayExpression(ClassInstanceReferenceVisitor visitor, PsiExpression expression, PsiElement referencedElement) {
    expression = CommonJavaRefactoringUtil.outermostParenthesizedExpression(expression);
    PsiElement parent = expression.getParent();
    if(parent instanceof PsiReferenceExpression && expression == ((PsiReferenceExpression) parent).getQualifierExpression()) {
      visitor.visitQualifier((PsiReferenceExpression) parent, expression, referencedElement);
    }
    else if (parent instanceof PsiTypeCastExpression) {
      visitor.visitTypeCast((PsiTypeCastExpression)parent, expression, referencedElement);
    }
    else if (parent instanceof PsiReturnStatement returnStatement) {
      PsiType returnType =
        PsiTreeUtil.getParentOfType(returnStatement, PsiMethod.class, PsiLambdaExpression.class) instanceof PsiMethod method
        ? method.getReturnType()
        : null;
      visitor.visitReadUsage(expression, returnType, referencedElement);
    }
    else if (parent instanceof PsiStatement) {
      visitor.visitReadUsage(expression, null, referencedElement);
    }
    else if (parent instanceof PsiExpressionList expressionList) {
      PsiElement pparent = expressionList.getParent();
      if (pparent instanceof PsiStatement) {
        visitor.visitReadUsage(expression, null, referencedElement);
      }
      else if (pparent instanceof PsiCallExpression callExpression) {
        PsiExpression[] arguments = callExpression.getArgumentList().getExpressions();
        PsiMethod method = callExpression.resolveMethod();
        if (method != null) {
          int index = ArrayUtil.indexOf(arguments, expression);
          if (index >= 0) {
            PsiParameter[] parameters = method.getParameterList().getParameters();
            if (parameters.length > index) {
              visitor.visitReadUsage(expression, parameters[index].getType(), referencedElement);
            }
          }
        }
      }
    }
    else if (parent instanceof PsiAssignmentExpression assignmentExpression) {
      if (expression.equals(assignmentExpression.getRExpression())) {
        visitor.visitReadUsage(expression, assignmentExpression.getLExpression().getType(), referencedElement);
      }
      else { // LExpression
        visitor.visitWriteUsage(expression, assignmentExpression.getRExpression().getType(), referencedElement);
      }
    }
    else if (RefactoringUtil.isAssignmentLHS(expression)) {
      visitor.visitWriteUsage(expression, null, referencedElement);
    }
    else if(parent instanceof PsiVariable) {
      visitor.visitReadUsage(expression, ((PsiVariable) parent).getType(), referencedElement);
    }
    else if(parent instanceof PsiExpression) {
      // for usages in expressions other than above, we do not care about the type
      visitor.visitReadUsage(expression, null, referencedElement);
    }
    else {
      LOG.error("Unknown variation of class instance usage");
    }
  }

}
