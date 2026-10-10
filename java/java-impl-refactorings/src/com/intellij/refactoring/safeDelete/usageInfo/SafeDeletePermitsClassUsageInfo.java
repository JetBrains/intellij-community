// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.safeDelete.usageInfo;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.refactoring.modplan.ModPlanEdit;
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteUsage;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.SealedUtils;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public class SafeDeletePermitsClassUsageInfo extends SafeDeleteReferenceUsageInfo implements ModPlanSafeDeleteUsage {
  private final SmartPsiElementPointer<PsiClass> myParentClass;
  private final boolean myChangeParentModifier;

  public SafeDeletePermitsClassUsageInfo(PsiJavaCodeReferenceElement reference, PsiClass refClass,
                                         PsiClass parentClass, boolean changeParentModifier) {
    super(reference, refClass, true);
    myParentClass = SmartPointerManager.createPointer(parentClass);
    myChangeParentModifier = changeParentModifier;
  }

  @Override
  public @Nullable PsiClass findReferencedElement() {
    return (PsiClass)super.findReferencedElement();
  }

  @Override
  public void deleteElement() throws IncorrectOperationException {
    if (myChangeParentModifier) {
      PsiClass refClass = findReferencedElement();
      PsiClass parentClass = myParentClass.getElement();
      if (refClass != null && parentClass != null) SealedUtils.removeFromPermitsList(parentClass, refClass);
    }
    else {
      PsiJavaCodeReferenceElement exChildRef = findReference();
      if (exChildRef != null) exChildRef.delete();
    }
  }

  @Override
  public boolean isSafeDelete() {
    return findReference() != null;
  }

  /**
   * Removes the reference from the permits list on the copies of the reference and of the parent class. The reference is found in
   * advance: a reference in a copy does not resolve to the deleted class of another file.
   */
  @Override
  public @Nullable ModPlanEdit asModPlanEdit() {
    PsiJavaCodeReferenceElement reference = findReference();
    PsiClass parentClass = myParentClass.getElement();
    if (reference == null || parentClass == null) return null;
    boolean changeParentModifier = myChangeParentModifier;
    return ModPlanEdit.update(List.of(reference, parentClass), copies -> {
      PsiJavaCodeReferenceElement referenceCopy = (PsiJavaCodeReferenceElement)copies.get(0);
      if (changeParentModifier) {
        SealedUtils.removeFromPermitsList((PsiClass)copies.get(1), referenceCopy);
      }
      else {
        referenceCopy.delete();
      }
    });
  }

  private @Nullable PsiJavaCodeReferenceElement findReference() {
    PsiClass parentClass = myParentClass.getElement();
    PsiReferenceList permitsList = parentClass == null ? null : parentClass.getPermitsList();
    if (permitsList == null) return null;
    PsiJavaCodeReferenceElement[] childRefs = permitsList.getReferenceElements();
    PsiClass refClass = findReferencedElement();
    if (childRefs.length < 1 || refClass == null) return null;
    return ContainerUtil.find(childRefs, ref -> ref.resolve() == refClass);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    SafeDeletePermitsClassUsageInfo info = (SafeDeletePermitsClassUsageInfo)o;
    return myChangeParentModifier == info.myChangeParentModifier &&
           SmartPointerManager.getInstance(getProject()).pointToTheSameElement(myParentClass, info.myParentClass);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), myParentClass, myChangeParentModifier);
  }
}
