package com.intellij.refactoring.inheritanceToDelegation;

import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

abstract class InnerClassMethod {
  final PsiMethod myMethod;

  public InnerClassMethod(PsiMethod method) {
    myMethod = method;
  }

  public abstract void createMethod(PsiClass innerClass)
          throws IncorrectOperationException;
}
