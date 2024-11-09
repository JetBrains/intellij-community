// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.reference;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.ClassUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;

public final class RefFieldImpl extends RefJavaElementImpl implements RefField {
  private static final int USED_FOR_READING_MASK             = 0b1_00000000_00000000; // 17th bit
  private static final int USED_FOR_WRITING_MASK             = 0b10_00000000_00000000; // 18th bit
  private static final int ASSIGNED_ONLY_IN_INITIALIZER_MASK = 0b100_00000000_00000000; // 19th bit
  private static final int IMPLICITLY_READ_MASK              = 0b1000_00000000_00000000; // 20th bit
  private static final int IMPLICITLY_WRITTEN_MASK           = 0b10000_00000000_00000000; // 21st bit
  private static final int IS_ENUM_CONSTANT                  = 0b100000_00000000_00000000; // 22nd bit

  RefFieldImpl(UField field, PsiElement psi, RefManager manager) {
    super(field, psi, manager);

    if (field instanceof UEnumConstant) {
      setEnumConstant(true);
    }
  }

  @Override
  protected synchronized void initialize() {
    PsiElement psi = getPsiElement();
    LOG.assertTrue(psi != null);
    UField uElement = getUastElement();
    LOG.assertTrue(uElement != null);
    WritableRefEntity parentRef = (WritableRefEntity)RefMethodImpl.findParentRef(psi, uElement, myManager);
    if (parentRef == null) return;
    if (!myManager.isDeclarationsFound()) {
      parentRef.add(this);
    }
    else {
      this.setOwner(parentRef);
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
  protected void markReferenced(@NotNull RefElementImpl refFrom, boolean forWriting, boolean forReading, UExpression expressionFrom) {
    addInReference(refFrom);

    boolean referencedFromClassInitializer = false;

    if (forWriting && expressionFrom != null) {
      UClassInitializer initializer = UastUtils.getParentOfType(expressionFrom, UClassInitializer.class);
      if (initializer != null && refFrom.getPsiElement() == UastUtils.getParentOfType(initializer, UClass.class).getSourcePsi()) {
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
  public synchronized boolean isUsedForReading() {
    if (checkFlag(USED_FOR_READING_MASK)) {
      return true;
    }
    RefClass ownerClass = getOwnerClass();
    // record fields are always implicitly read in hashCode() & equals()
    return ownerClass != null && ownerClass.isRecord();
  }

  private void setUsedForReading(boolean usedForReading) {
    setFlag(usedForReading, USED_FOR_READING_MASK);
  }

  @Override
  public synchronized boolean isUsedForWriting() {
    if (checkFlag(USED_FOR_WRITING_MASK)) {
      return true;
    }
    RefClass ownerClass = getOwnerClass();
    // record fields are always implicitly written in the constructor
    return ownerClass != null && ownerClass.isRecord();
  }

  private synchronized void setUsedForWriting(boolean usedForWriting) {
    setFlag(false, ASSIGNED_ONLY_IN_INITIALIZER_MASK);
    setFlag(usedForWriting, USED_FOR_WRITING_MASK);
  }

  @Override
  public boolean isOnlyAssignedInInitializer() {
    return checkFlag(ASSIGNED_ONLY_IN_INITIALIZER_MASK);
  }

  private void setEnumConstant(boolean enumConstant) {
    setFlag(enumConstant, IS_ENUM_CONSTANT);
  }

  @Override
  public boolean isEnumConstant() {
    return checkFlag(IS_ENUM_CONSTANT);
  }

  private void setImplicitlyRead(boolean implicitlyRead) {
    setFlag(implicitlyRead, IMPLICITLY_READ_MASK);
  }

  @Override
  public boolean isImplicitlyRead() {
    return checkFlag(IMPLICITLY_READ_MASK);
  }

  private void setImplicitlyWritten(boolean implicitlyWritten) {
    setFlag(implicitlyWritten, IMPLICITLY_WRITTEN_MASK);
  }

  @Override
  public boolean isImplicitlyWritten() {
    return checkFlag(IMPLICITLY_WRITTEN_MASK);
  }

  @Override
  public void accept(@NotNull final RefVisitor visitor) {
    if (visitor instanceof RefJavaVisitor javaVisitor) {
      ReadAction.run(() -> javaVisitor.visitField(this));
    }
    else {
      super.accept(visitor);
    }
  }

  @Override
  public void buildReferences() {
    UField uField = getUastElement();
    if (uField != null) {
      final RefJavaUtil refUtil = RefJavaUtil.getInstance();
      refUtil.addReferencesTo(uField, this, uField.getUastInitializer());
      refUtil.addReferencesTo(uField, this, uField.getUAnnotations().toArray(UElementKt.EMPTY_ARRAY));
      if (uField instanceof UEnumConstant) {
        refUtil.addReferencesTo(uField, this, uField);
      }

      if (uField.getUastInitializer() != null || uField instanceof UEnumConstant) {
        setInitializerMasks();
      }
      else if (RefUtil.isImplicitWrite(uField.getJavaPsi())) {
        setImplicitlyWritten(true);
        setInitializerMasks();
      }

      if (RefUtil.isImplicitRead(uField.getJavaPsi())) {
        setImplicitlyRead(true);
      }

      refUtil.addTypeReference(uField, uField.getType(), getRefManager(), this);
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
    WritableRefEntity owner = getOwner();
    if (owner == null) {
      LOG.error("No parent class for: " + getName());
      return null;
    }
    return owner.getExternalName() + " " + getName();
  }

  @Nullable
  static RefField fieldFromExternalName(RefManager manager, String externalName) {
    return (RefField)manager.getReference(findPsiField(PsiManager.getInstance(manager.getProject()), externalName));
  }

  @SuppressWarnings("WeakerAccess") // used by TeamCity
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
