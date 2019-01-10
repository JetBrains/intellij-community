// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.reference;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.*;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;

/**
 * @author max
 */
public class RefFieldImpl extends RefJavaElementImpl implements RefField {
  private static final int USED_FOR_READING_MASK = 0x10000;
  private static final int USED_FOR_WRITING_MASK = 0x20000;
  private static final int ASSIGNED_ONLY_IN_INITIALIZER_MASK = 0x40000;

  RefFieldImpl(UField field, PsiElement psi, RefManager manager) {
    super(field, psi, manager);
    if (psi instanceof UElement) {
      LOG.error(new Exception("psi should not be uast element: " + psi));
    }

    if (field instanceof UEnumConstant) {
      putUserData(ENUM_CONSTANT, true);
    }
  }

  @Override
  protected void initialize() {
    PsiElement psi = getPsiElement();
    LOG.assertTrue(psi != null);
    UField uElement = getUastElement();
    LOG.assertTrue(uElement != null);
    RefElement owner = RefMethodImpl.findParentRef(psi, uElement, myManager);
    ((WritableRefEntity)owner).add(this);

    if (owner instanceof RefClass && ((RefClass)owner).isInterface()) {
      setIsStatic(true);
      setIsFinal(true);
    }
  }

  @Deprecated
  @Override
  public PsiField getElement() {
    return (PsiField)super.getPsiElement();
  }

  @Override
  public UField getUastElement() {
    return UastContextKt.toUElement(getPsiElement(), UField.class);
  }

  @Override
  protected void markReferenced(RefElementImpl refFrom, PsiElement psiFrom, PsiElement psiWhat, boolean forWriting, boolean forReading, UExpression expressionFrom) {
    addInReference(refFrom);

    boolean referencedFromClassInitializer = false;

    if (forWriting && expressionFrom != null) {
      UClassInitializer initializer = UastUtils.getParentOfType(expressionFrom, UClassInitializer.class);
      if (initializer != null && psiFrom == UastUtils.getParentOfType(initializer, UClass.class).getSourcePsi()) {
        UExpression qualifierExpression = expressionFrom instanceof UQualifiedReferenceExpression ? ((UQualifiedReferenceExpression)expressionFrom).getReceiver() : null;
        if (qualifierExpression == null || qualifierExpression instanceof UThisExpression) {
          referencedFromClassInitializer = true;
        }
      }
    }

    if (forWriting) {
      setUsedForWriting(true);
    }

    if (forReading) {
      setUsedForReading(true);
    }
    
    setForbidProtectedAccess(refFrom, expressionFrom);
    getRefManager().fireNodeMarkedReferenced(this, refFrom, referencedFromClassInitializer, forReading, forWriting, expressionFrom == null ? null : expressionFrom.getSourcePsi());
  }

  @Override
  public boolean isUsedForReading() {
    return checkFlag(USED_FOR_READING_MASK);
  }

  private void setUsedForReading(boolean usedForReading) {
    setFlag(usedForReading, USED_FOR_READING_MASK);
  }

  @Override
  public boolean isUsedForWriting() {
    return checkFlag(USED_FOR_WRITING_MASK);
  }

  private void setUsedForWriting(boolean usedForWriting) {
    setFlag(false, ASSIGNED_ONLY_IN_INITIALIZER_MASK);
    setFlag(usedForWriting, USED_FOR_WRITING_MASK);
  }

  @Override
  public boolean isOnlyAssignedInInitializer() {
    return checkFlag(ASSIGNED_ONLY_IN_INITIALIZER_MASK);
  }

  @Override
  public void accept(@NotNull final RefVisitor visitor) {
    if (visitor instanceof RefJavaVisitor) {
      ApplicationManager.getApplication().runReadAction(() -> ((RefJavaVisitor)visitor).visitField(this));
    }  else {
      super.accept(visitor);
    }
  }

  @Override
  public void buildReferences() {
    UField uField = getUastElement();
    if (uField != null) {
      final RefJavaUtil refUtil = RefJavaUtil.getInstance();
      refUtil.addReferencesTo(uField, this, uField.getUastInitializer());
      refUtil.addReferencesTo(uField, this, ((UAnnotated)uField).getAnnotations().toArray(UElementKt.EMPTY_ARRAY));
      if (uField instanceof UEnumConstant) {
        refUtil.addReferencesTo(uField, this, uField);
      }

      if (uField.getUastInitializer() != null || uField instanceof UEnumConstant) {
        setInitializerMasks();
      }
      else if (RefUtil.isImplicitWrite(uField.getJavaPsi())) {
        putUserData(IMPLICITLY_WRITTEN, true);
        setInitializerMasks();
      }

      if (RefUtil.isImplicitRead(uField.getJavaPsi())) {
        putUserData(IMPLICITLY_READ, true);
      }

      refUtil.addTypeReference((UElement)uField, uField.getType(), getRefManager(), this);
      getRefManager().fireBuildReferences(this);
    }
  }

  private void setInitializerMasks() {
    if (!checkFlag(USED_FOR_WRITING_MASK)) {
      setFlag(true, ASSIGNED_ONLY_IN_INITIALIZER_MASK);
      setFlag(true, USED_FOR_WRITING_MASK);
    }
  }

  @Override
  public RefClass getOwnerClass() {
    return ObjectUtils.tryCast(getOwner(), RefClass.class);
  }

  @Override
  public String getExternalName() {
    return ReadAction.compute(() -> {
      UField uField = getUastElement();
      if (uField == null) return null;
      return PsiFormatUtil.getExternalName((PsiModifierListOwner)uField.getJavaPsi());
    });
  }

  @Nullable
  static RefField fieldFromExternalName(RefManager manager, String externalName) {
    return (RefField)manager.getReference(findPsiField(PsiManager.getInstance(manager.getProject()), externalName));
  }

  @Nullable
  public static PsiField findPsiField(PsiManager manager, String externalName) {
    int classNameDelimiter = externalName.lastIndexOf(' ');
    if (classNameDelimiter > 0 && classNameDelimiter < externalName.length() - 1) {
      final String className = externalName.substring(0, classNameDelimiter);
      final PsiClass psiClass = ClassUtil.findPsiClass(manager, className);
      if (psiClass != null) {
        final String fieldName = externalName.substring(classNameDelimiter + 1);
        return psiClass.findFieldByName(fieldName, false);
      }
    }
    return null;
  }

  @Override
  public boolean isSuspicious() {
    if (isEntry()) return false;
    return super.isSuspicious() || isUsedForReading() != isUsedForWriting();
  }
}
