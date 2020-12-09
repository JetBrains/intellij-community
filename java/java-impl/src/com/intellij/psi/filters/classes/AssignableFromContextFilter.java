// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.filters.classes;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.reference.SoftReference;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.NotNull;

import static com.intellij.util.ObjectUtils.tryCast;

public class AssignableFromContextFilter implements ElementFilter {

  private SoftReference<PsiElement> myCurrentContext = new SoftReference<>(null);
  private SoftReference<PsiClass> myCachedClass = new SoftReference<>(null);

  @Override
  public boolean isClassAcceptable(Class hintClass) {
    return ReflectionUtil.isAssignable(PsiClass.class, hintClass);
  }

  @Override
  public boolean isAcceptable(Object element, PsiElement context) {
    if (myCurrentContext.get() != context) {
      myCurrentContext = new SoftReference<>(context);
      myCachedClass = new SoftReference<>(PsiTreeUtil.getContextOfType(context, false, PsiClass.class));
    }
    PsiClass curClass = myCachedClass.get();
    if (curClass == null) return false;
    PsiClass candidate = tryCast(element, PsiClass.class);
    if (candidate == null) return false;
    return checkInheritance(curClass, candidate);
  }

  protected boolean checkInheritance(@NotNull PsiClass curClass, @NotNull PsiClass candidate) {
    String qualifiedName = curClass.getQualifiedName();
    return qualifiedName != null && (qualifiedName.equals(candidate.getQualifiedName()) || candidate.isInheritor(curClass, true));
  }

  public String toString() {
    return "assignable-from-context";
  }
}


