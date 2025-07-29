// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.types;

import com.intellij.codeInspection.dataFlow.*;
import com.intellij.codeInspection.dataFlow.jvm.SpecialField;
import com.intellij.psi.PsiModifierListOwner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Set;

import static java.util.Objects.hash;

public class DfReferenceConstantType extends DfConstantType<Object> implements DfReferenceType {
  private final @NotNull TypeConstraint myConstraint;
  private final @NotNull Mutability myMutability;
  private final @Nullable SpecialField myJvmSpecialField;
  private final @NotNull DfType mySpecialFieldType;
  private final boolean myDropConstantOnWiden;

  DfReferenceConstantType(@NotNull Object constant, @NotNull TypeConstraint type, boolean dropConstantOnWiden) {
    super(constant);
    myConstraint = type;
    myMutability = constant instanceof PsiModifierListOwner ? Mutability.getMutability((PsiModifierListOwner)constant) : Mutability.UNKNOWN;
    myJvmSpecialField = SpecialField.fromQualifierType(this);
    mySpecialFieldType = myJvmSpecialField == null ? BOTTOM : myJvmSpecialField.fromConstant(constant);
    myDropConstantOnWiden = dropConstantOnWiden;
  }

  @Override
  public DfType widen() {
    if (myDropConstantOnWiden) {
      return new DfGenericObjectType(Set.of(), myConstraint, DfaNullability.NOT_NULL, myMutability,
                                     myJvmSpecialField, mySpecialFieldType.widen(), false);
    }
    return this;
  }

  @Override
  public @NotNull DfType meet(@NotNull DfType other) {
    if (other.isSuperType(this)) return this;
    if (other instanceof DfEphemeralReferenceType) return BOTTOM;
    if (other instanceof DfGenericObjectType) {
      DfReferenceType type = ((DfReferenceType)other).dropMutability();
      if (type.isSuperType(this)) return this;
      TypeConstraint constraint = type.getConstraint().meet(myConstraint);
      if (constraint != TypeConstraints.BOTTOM) {
        DfReferenceConstantType subConstant = new DfReferenceConstantType(getValue(), constraint, myDropConstantOnWiden);
        if (type.isSuperType(subConstant)) return subConstant;
      }
    }
    return BOTTOM;
  }

  @Override
  public @NotNull DfaNullability getNullability() {
    return DfaNullability.NOT_NULL;
  }

  @Override
  public @NotNull TypeConstraint getConstraint() {
    return myConstraint;
  }

  @Override
  public @NotNull Mutability getMutability() {
    return myMutability;
  }

  @Override
  public @Nullable SpecialField getSpecialField() {
    return myJvmSpecialField;
  }

  @Override
  public @NotNull DfType getSpecialFieldType() {
    return mySpecialFieldType;
  }

  @Override
  public DfType tryNegate() {
    return new DfGenericObjectType(Set.of(getValue()), TypeConstraints.TOP, DfaNullability.UNKNOWN, Mutability.UNKNOWN,
                                   null, BOTTOM, false);
  }

  @Override
  public @NotNull DfReferenceType dropNullability() {
    // Nullable constant is not constant anymore
    return new DfGenericObjectType(
      Set.of(), myConstraint, DfaNullability.UNKNOWN, myMutability, myJvmSpecialField, mySpecialFieldType, false);
  }
  
  @Override
  public @NotNull DfReferenceType convert(TypeConstraints.@NotNull TypeConstraintFactory factory) {
    return new DfReferenceConstantType(getValue(), myConstraint.convert(factory), myDropConstantOnWiden);
  }

  @Override
  public @NotNull DfType join(@NotNull DfType other) {
    if (other instanceof DfGenericObjectType || other instanceof DfEphemeralReferenceType) {
      return other.join(this);
    }
    if (isSuperType(other)) return this;
    if (other.isSuperType(this)) return other;
    if (!(other instanceof DfReferenceType type)) return TOP;
    TypeConstraint constraint = getConstraint().join(type.getConstraint());
    DfaNullability nullability = getNullability().unite(type.getNullability());
    Mutability mutability = getMutability().join(type.getMutability());
    boolean locality = isLocal() && type.isLocal();
    SpecialField sf = Objects.equals(getSpecialField(), type.getSpecialField()) ? getSpecialField() : null;
    DfType sfType = sf == null ? BOTTOM : getSpecialFieldType().join(type.getSpecialFieldType());
    if (constraint.isSingleton() && nullability == DfaNullability.NOT_NULL) {
      return new DfReferenceConstantType(constraint, constraint, false);
    }
    return new DfGenericObjectType(Set.of(), constraint, nullability, mutability, sf, sfType, locality);
  }

  @Override
  public @Nullable DfType tryJoinExactly(@NotNull DfType other) {
    if (isMergeable(other)) return this;
    if (other.isMergeable(this)) return other;
    if (other instanceof DfGenericObjectType) {
      other.tryJoinExactly(this);
    }
    return null;
  }

  @Override
  public boolean equals(Object obj) {
    return super.equals(obj) && 
           obj instanceof DfReferenceConstantType refConstant &&
           myConstraint.equals(refConstant.myConstraint);
  }

  @Override
  public int hashCode() {
    return hash(super.hashCode(), myConstraint);
  }

  @Override
  public @NotNull String toString() {
    return DfaPsiUtil.renderValue(getValue());
  }
}
