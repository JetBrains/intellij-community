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
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

/**
 * @author ven
 */
public class SafeDeleteExtendsClassUsageInfo extends SafeDeleteReferenceUsageInfo {
  private static final Logger LOG = Logger.getInstance(SafeDeleteExtendsClassUsageInfo.class);
  private final PsiClass myExtendingClass;
  private final PsiSubstitutor mySubstitutor;

  public SafeDeleteExtendsClassUsageInfo(final PsiJavaCodeReferenceElement reference, PsiClass refClass, PsiClass extendingClass) {
    super(reference, refClass, true);
    myExtendingClass = extendingClass;
    mySubstitutor = TypeConversionUtil.getClassSubstitutor(refClass, myExtendingClass, PsiSubstitutor.EMPTY);
    LOG.assertTrue(mySubstitutor != null);
  }

  @Override
  public PsiClass getReferencedElement() {
    return (PsiClass)super.getReferencedElement();
  }

  @Override
  public void deleteElement() throws IncorrectOperationException {
    final PsiElement parent = getElement().getParent();
    LOG.assertTrue(parent instanceof PsiReferenceList);
    final PsiClass refClass = getReferencedElement();
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(refClass.getProject());

    boolean targetTypeParameter = myExtendingClass instanceof PsiTypeParameter;
    copyExtendsList(refClass.getExtendsList(), refClass.isInterface() == myExtendingClass.isInterface() || targetTypeParameter, elementFactory);
    copyExtendsList(refClass.getImplementsList(), targetTypeParameter, elementFactory);

    getElement().delete();
  }

  private void copyExtendsList(@Nullable PsiReferenceList sourceExtendsList,
                               boolean targetExtends,
                               PsiElementFactory elementFactory) {
    if (sourceExtendsList != null) {
      final PsiClassType[] referenceTypes = sourceExtendsList.getReferencedTypes();
      final PsiReferenceList targetExtendsList = targetExtends ? myExtendingClass.getExtendsList() : myExtendingClass.getImplementsList();
      final PsiClassType[] existingRefTypes = targetExtendsList.getReferencedTypes();
      for (PsiClassType referenceType : referenceTypes) {
        if (ArrayUtilRt.find(existingRefTypes, referenceType) > -1) continue;
        PsiClassType classType = (PsiClassType)mySubstitutor.substitute(referenceType);
        PsiElement extendsRef = targetExtendsList.add(elementFactory.createReferenceElementByType(classType));
        CodeStyleManager.getInstance(myExtendingClass.getProject()).reformat(extendsRef);
      }
    }
  }

  @Override
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
