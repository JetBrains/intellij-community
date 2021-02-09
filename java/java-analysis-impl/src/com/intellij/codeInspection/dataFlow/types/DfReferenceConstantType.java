// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.types;

import com.intellij.codeInspection.dataFlow.*;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Set;

import static com.intellij.codeInspection.dataFlow.types.DfTypes.BOTTOM;
import static com.intellij.codeInspection.dataFlow.types.DfTypes.TOP;

public class DfReferenceConstantType extends DfConstantType<Object> implements DfReferenceType {
  private final @NotNull PsiType myPsiType;
  private final @NotNull TypeConstraint myConstraint;
  private final @NotNull Mutability myMutability;
  private final @Nullable SpecialField mySpecialField;
  private final @NotNull DfType mySpecialFieldType;
  private final boolean myDropConstantOnWiden;
  
  DfReferenceConstantType(@NotNull Object constant, @NotNull PsiType psiType, @NotNull TypeConstraint type, boolean dropConstantOnWiden) {
    super(constant);
    myPsiType = psiType;
    myConstraint = type;
    myMutability = constant instanceof PsiModifierListOwner ? Mutability.getMutability((PsiModifierListOwner)constant) : Mutability.UNKNOWN;
    mySpecialField = SpecialField.fromQualifierType(psiType);
    mySpecialFieldType = mySpecialField == null ? BOTTOM : mySpecialField.fromConstant(constant);
    myDropConstantOnWiden = dropConstantOnWiden;
  }

  @Override
  public DfType widen() {
    if (myDropConstantOnWiden) {
      return new DfGenericObjectType(Set.of(), myConstraint, DfaNullability.NOT_NULL, myMutability, 
                                     mySpecialField, mySpecialFieldType.widen(), false);
    }
    return this;
  }

  @NotNull
  @Override
  public DfType meet(@NotNull DfType other) {
    if (other.isSuperType(this)) return this;
    if (other instanceof DfEphemeralReferenceType) return BOTTOM;
    if (other instanceof DfGenericObjectType) {
      DfReferenceType type = ((DfReferenceType)other).dropMutability();
      if (type.isSuperType(this)) return this;
      TypeConstraint constraint = type.getConstraint().meet(myConstraint);
      if (constraint != TypeConstraints.BOTTOM) {
        DfReferenceConstantType subConstant = new DfReferenceConstantType(getValue(), myPsiType, constraint, myDropConstantOnWiden);
        if (type.isSuperType(subConstant)) return subConstant;
      }
    }
    return BOTTOM;
  }

  @NotNull
  @Override
  public PsiType getPsiType() {
    return myPsiType;
  }

  @NotNull
  @Override
  public DfaNullability getNullability() {
    return DfaNullability.NOT_NULL;
  }

  @NotNull
  @Override
  public TypeConstraint getConstraint() {
    return myConstraint;
  }

  @NotNull
  @Override
  public Mutability getMutability() {
    return myMutability;
  }

  @Nullable
  @Override
  public SpecialField getSpecialField() {
    return mySpecialField;
  }

  @NotNull
  @Override
  public DfType getSpecialFieldType() {
    return mySpecialFieldType;
  }

  @Override
  public DfType tryNegate() {
    return new DfGenericObjectType(Set.of(getValue()), TypeConstraints.TOP, DfaNullability.UNKNOWN, Mutability.UNKNOWN,
                                   null, BOTTOM, false);
  }

  @NotNull
  @Override
  public DfReferenceType dropNullability() {
    return this;
  }

  @NotNull
  @Override
  public DfType join(@NotNull DfType other) {
    if (other instanceof DfGenericObjectType || other instanceof DfEphemeralReferenceType) {
      return other.join(this);
    }
    if (isSuperType(other)) return this;
    if (other.isSuperType(this)) return other;
    if (!(other instanceof DfReferenceType)) return TOP;
    DfReferenceType type = (DfReferenceType)other;
    TypeConstraint constraint = getConstraint().join(type.getConstraint());
    DfaNullability nullability = getNullability().unite(type.getNullability());
    Mutability mutability = getMutability().unite(type.getMutability());
    boolean locality = isLocal() && type.isLocal();
    SpecialField sf = Objects.equals(getSpecialField(), type.getSpecialField()) ? getSpecialField() : null;
    DfType sfType = sf == null ? BOTTOM : getSpecialFieldType().join(type.getSpecialFieldType());
    return new DfGenericObjectType(Set.of(), constraint, nullability, mutability, sf, sfType, locality);
  }
}
