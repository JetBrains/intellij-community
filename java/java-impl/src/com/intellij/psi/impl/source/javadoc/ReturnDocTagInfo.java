package com.intellij.psi.impl.source.javadoc;

import com.intellij.psi.javadoc.JavadocTagInfo;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.util.ArrayUtil;

/**
 * @author mike
 */
class ReturnDocTagInfo implements JavadocTagInfo {
  public String checkTagValue(PsiDocTagValue value) {
    return null;
  }

  public String getName() {
    return "return";
  }

  public Object[] getPossibleValues(PsiElement context, PsiElement place, String prefix) {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  public PsiReference getReference(PsiDocTagValue value) {
    return null;
  }

  public boolean isValidInContext(PsiElement element) {
    if (!(element instanceof PsiMethod)) return false;
    PsiMethod method = (PsiMethod)element;
    final PsiType type = method.getReturnType();
    if (type == null) return false;
    return type != PsiType.VOID;
  }

  public boolean isInline() {
    return false;
  }
}
