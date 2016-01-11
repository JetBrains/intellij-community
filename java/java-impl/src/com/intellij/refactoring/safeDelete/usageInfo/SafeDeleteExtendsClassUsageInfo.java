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
package com.intellij.refactoring.safeDelete.usageInfo;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;

/**
 * @author ven
 */
public class SafeDeleteExtendsClassUsageInfo extends SafeDeleteReferenceUsageInfo {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteExtendsClassUsageInfo");
  private final PsiClass myExtendingClass;
  private final PsiSubstitutor mySubstitutor;

  public SafeDeleteExtendsClassUsageInfo(final PsiJavaCodeReferenceElement reference, PsiClass refClass, PsiClass extendingClass) {
    super(reference, refClass, true);
    myExtendingClass = extendingClass;
    mySubstitutor = TypeConversionUtil.getClassSubstitutor(refClass, myExtendingClass, PsiSubstitutor.EMPTY);
    LOG.assertTrue(mySubstitutor != null);
  }

  public PsiClass getReferencedElement() {
    return (PsiClass)super.getReferencedElement();
  }

  public void deleteElement() throws IncorrectOperationException {
    final PsiElement parent = getElement().getParent();
    LOG.assertTrue(parent instanceof PsiReferenceList);
    final PsiClass refClass = getReferencedElement();
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(refClass.getProject()).getElementFactory();

    final PsiReferenceList extendsList = refClass.getExtendsList();
    final PsiReferenceList extendingImplementsList = myExtendingClass instanceof PsiTypeParameter ? myExtendingClass.getExtendsList() : myExtendingClass.getImplementsList();
    if (extendsList != null) {
      final PsiClassType[] referenceTypes = extendsList.getReferencedTypes();
      final PsiReferenceList listToAddExtends = refClass.isInterface() == myExtendingClass.isInterface() || myExtendingClass instanceof PsiTypeParameter ? myExtendingClass.getExtendsList() : extendingImplementsList;
      final PsiClassType[] existingRefTypes = listToAddExtends.getReferencedTypes();
      for (PsiClassType referenceType : referenceTypes) {
        if (ArrayUtilRt.find(existingRefTypes, referenceType) > -1) continue;
        listToAddExtends.add(elementFactory.createReferenceElementByType((PsiClassType)mySubstitutor.substitute(referenceType)));
      }
    }

    final PsiReferenceList implementsList = refClass.getImplementsList();
    if (implementsList != null) {
      final PsiClassType[] existingRefTypes = extendingImplementsList.getReferencedTypes();
      PsiClassType[] referenceTypes = implementsList.getReferencedTypes();
      for (PsiClassType referenceType : referenceTypes) {
        if (ArrayUtilRt.find(existingRefTypes, referenceType) > -1) continue;
        extendingImplementsList.add(elementFactory.createReferenceElementByType((PsiClassType)mySubstitutor.substitute(referenceType)));
      }
    }

    getElement().delete();
  }

  public boolean isSafeDelete() {
    if (getElement() == null) return false;
    final PsiClass refClass = getReferencedElement();
    if (refClass.getExtendsListTypes().length > 0) {
      final PsiReferenceList listToAddExtends = refClass.isInterface() == myExtendingClass.isInterface() ? myExtendingClass.getExtendsList() :
                                                myExtendingClass.getImplementsList();
      if (listToAddExtends == null) return false;
    }

    if (refClass.getImplementsListTypes().length > 0) {
      if (myExtendingClass.getImplementsList() == null) return false;
    }

    return true;
  }
}
