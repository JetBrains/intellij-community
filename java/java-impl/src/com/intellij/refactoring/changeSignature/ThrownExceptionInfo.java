package com.intellij.refactoring.changeSignature;

import com.intellij.psi.*;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.util.IncorrectOperationException;

/**
 * @author ven
 */
public class ThrownExceptionInfo {
  int oldIndex;
  CanonicalTypes.Type myType;

  public ThrownExceptionInfo() {
    oldIndex = -1;
  }

  public ThrownExceptionInfo(int oldIndex) {
    this.oldIndex = oldIndex;
  }

  public ThrownExceptionInfo(int oldIndex, PsiClassType type) {
    this.oldIndex = oldIndex;
    setType(type);
  }

  public void setType(PsiClassType type) {
    myType = CanonicalTypes.createTypeWrapper(type);
  }

  public PsiType createType(PsiElement context, final PsiManager manager) throws IncorrectOperationException {
    if (myType != null) {
      return myType.getType(context, manager);
    } else {
      return null;
    }
  }

  String getTypeText() {
    if (myType != null) {
      return myType.getTypeText();
    }
    else {
      return "";
    }
  }

  public void updateFromMethod(PsiMethod method) {
    if (myType != null) return;
    PsiClassType[] types = method.getThrowsList().getReferencedTypes();
    if (oldIndex >= 0) {
      setType(types[oldIndex]);
    }
  }

  public int getOldIndex() {
    return oldIndex;
  }
}
