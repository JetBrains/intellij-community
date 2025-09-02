// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.safeDelete.usageInfo;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.SealedUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

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
    copyExtendsList(refClass, refClass.getExtendsList(), refClass.isInterface() == myExtendingClass.isInterface() || targetTypeParameter, elementFactory);
    copyExtendsList(refClass, refClass.getImplementsList(), targetTypeParameter, elementFactory);

    getElement().delete();

    if (!refClass.hasModifierProperty(PsiModifier.SEALED)) return;
    SealedUtils.removeFromPermitsList(refClass, myExtendingClass);
    final PsiModifierList modifiers = myExtendingClass.getModifierList();
    if (modifiers == null || !modifiers.hasModifierProperty(PsiModifier.NON_SEALED)) return;
    if (!SealedUtils.hasSealedParent(myExtendingClass)) {
      modifiers.setModifierProperty(PsiModifier.NON_SEALED, false);
    }
  }

  private void copyExtendsList(@NotNull PsiClass classToRemove,
                               @Nullable PsiReferenceList sourceExtendsList,
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
        PsiClass classToExtend = classType.resolve();
        if (classToExtend != null && classToExtend.hasModifierProperty(PsiModifier.SEALED)) {
          String extendingClassName = Objects.requireNonNull(myExtendingClass.getQualifiedName());
          if (classToExtend.getPermitsList() == null) {
            if (classToExtend.getContainingFile() != myExtendingClass.getContainingFile()) {
              Collection<String> missingInheritors = new ArrayList<>(SealedUtils.findSameFileInheritors(classToExtend, classToRemove));
              missingInheritors.add(extendingClassName);
              SealedUtils.fillPermitsList(classToExtend, missingInheritors);
            }
          }
          else {
            SealedUtils.fillPermitsList(classToExtend, Collections.singleton(extendingClassName));
          }
        }
        CodeStyleManager.getInstance(myExtendingClass.getProject()).reformat(extendsRef);
      }
    }
  }

  @Override
  public boolean isSafeDelete() {
    if (getElement() == null) return false;
    final PsiClass classToRemove = getReferencedElement();
    if (classToRemove.getExtendsListTypes().length > 0) {
      final PsiReferenceList listToAddExtends = classToRemove.isInterface() == myExtendingClass.isInterface() ?
                                                myExtendingClass.getExtendsList() :
                                                myExtendingClass.getImplementsList();
      if (listToAddExtends == null) return false;
    }

    if (classToRemove.getImplementsListTypes().length > 0) {
      if (myExtendingClass.getImplementsList() == null) return false;
    }

    PsiResolveHelper resolveHelper = PsiResolveHelper.getInstance(getProject());
    return StreamEx.of(classToRemove.getInterfaces()).prepend(classToRemove.getSuperClass()).nonNull()
      .allMatch(grandParent -> resolveHelper.isAccessible(grandParent, myExtendingClass, null));
  }
}
