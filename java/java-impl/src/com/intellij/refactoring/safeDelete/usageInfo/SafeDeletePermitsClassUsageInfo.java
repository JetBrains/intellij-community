// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.safeDelete.usageInfo;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiReferenceList;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ClassUtils;

import java.util.Objects;

public class SafeDeletePermitsClassUsageInfo extends SafeDeleteReferenceUsageInfo {
  private final PsiClass myParentClass;

  public SafeDeletePermitsClassUsageInfo(final PsiJavaCodeReferenceElement reference, PsiClass refClass, PsiClass parentClass) {
    super(reference, refClass, true);
    myParentClass = parentClass;
  }

  @Override
  public PsiClass getReferencedElement() {
    return (PsiClass)super.getReferencedElement();
  }

  @Override
  public void deleteElement() throws IncorrectOperationException {
    final PsiClass refClass = getReferencedElement();
    ClassUtils.removeFromPermitsList(myParentClass, refClass);
  }

  @Override
  public boolean isSafeDelete() {
    if (getElement() != null) return true;
    PsiReferenceList permitsList = myParentClass.getPermitsList();
    if (permitsList == null) return false;
    PsiJavaCodeReferenceElement[] childRefs = permitsList.getReferenceElements();
    if (childRefs.length < 1) return false;
    return ContainerUtil.exists(childRefs, ref -> ref.resolve() == getReferencedElement());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    SafeDeletePermitsClassUsageInfo info = (SafeDeletePermitsClassUsageInfo)o;
    return Objects.equals(myParentClass, info.myParentClass);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), myParentClass);
  }
}
