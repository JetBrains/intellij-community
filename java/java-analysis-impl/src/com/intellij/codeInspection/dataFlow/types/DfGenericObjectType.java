// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.types;

import com.intellij.codeInspection.dataFlow.*;
import com.intellij.codeInspection.dataFlow.jvm.SpecialField;
import com.intellij.codeInspection.dataFlow.value.RelationType;
import com.intellij.java.JavaBundle;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiEnumConstant;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

final class DfGenericObjectType extends DfAntiConstantType<Object> implements DfReferenceType {
  private final @NotNull TypeConstraint myConstraint;
  private final @NotNull DfaNullability myNullability;
  private final @NotNull Mutability myMutability;
  private final @Nullable SpecialField mySpecialField;
  private final @NotNull DfType mySpecialFieldType;
  private final boolean myLocal;

  DfGenericObjectType(@NotNull Set<Object> notValues,
                      @NotNull TypeConstraint constraint,
                      @NotNull DfaNullability nullability,
                      @NotNull Mutability mutability,
                      @Nullable SpecialField field,
                      @NotNull DfType type,
                      boolean local) {
    super(notValues);
    assert constraint != TypeConstraints.BOTTOM;
    myConstraint = constraint;
    myNullability = nullability;
    myMutability = mutability;
    mySpecialField = field;
    mySpecialFieldType = type instanceof DfReferenceType ? ((DfReferenceType)type).dropSpecialField() : type;
    myLocal = local;
  }

  @NotNull
  @Override
  public DfaNullability getNullability() {
    return myNullability;
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

  @Override
  public boolean isLocal() {
    return myLocal;
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
    if (myMutability != Mutability.UNKNOWN || myLocal || mySpecialField != null) {
      return null;
    }
    TypeConstraint negated = myConstraint.tryNegate();
    if (negated == null) return null;
    DfType result = negated.asDfType();
    return myNullability == DfaNullability.NOT_NULL ? result.join(DfTypes.NULL) : result.meet(DfTypes.NOT_NULL_OBJECT);
  }

  @NotNull
  @Override
  public Set<Object> getNotValues() {
    if (myNullability == DfaNullability.NOT_NULL) {
      Set<Object> values = new HashSet<>(myNotValues);
      values.add(null);
      return Collections.unmodifiableSet(values);
    }
    return super.getNotValues();
  }

  Set<Object> getRawNotValues() {
    return super.getNotValues();
  }

  @NotNull
  @Override
  public DfReferenceType dropTypeConstraint() {
    return myConstraint == TypeConstraints.TOP ? this :
           new DfGenericObjectType(myNotValues, TypeConstraints.TOP, myNullability, myMutability, mySpecialField, mySpecialFieldType,
                                   myLocal);
  }

  @NotNull
  @Override
  public DfReferenceType dropMutability() {
    return myMutability == Mutability.UNKNOWN ? this :
           new DfGenericObjectType(myNotValues, myConstraint, myNullability, Mutability.UNKNOWN, mySpecialField, mySpecialFieldType,
                                   myLocal);
  }

  @NotNull
  @Override
  public DfReferenceType dropLocality() {
    return myLocal ? new DfGenericObjectType(myNotValues, myConstraint, myNullability, myMutability, mySpecialField, mySpecialFieldType,
                                             false) : this;
  }

  @NotNull
  @Override
  public DfReferenceType dropNullability() {
    return myNullability == DfaNullability.UNKNOWN ? this :
           new DfGenericObjectType(myNotValues, myConstraint, DfaNullability.UNKNOWN, myMutability, mySpecialField, mySpecialFieldType,
                                   myLocal);
  }

  @NotNull
  @Override
  public DfReferenceType dropSpecialField() {
    return mySpecialField == null ? this :
           new DfGenericObjectType(myNotValues, myConstraint, myNullability, myMutability, null, BOTTOM, myLocal);
  }

  @Override
  public boolean isSuperType(@NotNull DfType other) {
    if (other == BOTTOM) return true;
    if (other instanceof DfNullConstantType) {
      return getNullability() != DfaNullability.NOT_NULL;
    }
    if (!(other instanceof DfReferenceType)) return false;
    if (!myNotValues.isEmpty()) {
      if (other instanceof DfReferenceConstantType) {
        if (myNotValues.contains(((DfReferenceConstantType)other).getValue())) {
          return false;
        }
      }
      else if (other instanceof DfGenericObjectType) {
        if (!((DfGenericObjectType)other).myNotValues.containsAll(myNotValues)) {
          return false;
        }
      }
      else return false;
    }
    DfReferenceType type = (DfReferenceType)other;
    if (isLocal() && !type.isLocal() && !getConstraint().isComparedByEquals()) return false;
    if (type.getNullability() != getNullability() && getNullability() != DfaNullability.UNKNOWN &&
        type.getNullability() != DfaNullability.NOT_NULL) return false;
    if (!getConstraint().isSuperConstraintOf(type.getConstraint())) return false;
    if (getMutability().join(type.getMutability()) != getMutability()) return false;
    SpecialField sf = getSpecialField();
    if (sf != null) {
      if (sf != type.getSpecialField()) return false;
      if (!getSpecialFieldType().isSuperType(type.getSpecialFieldType())) return false;
    }
    return true;
  }

  @Override
  public boolean isMergeable(@NotNull DfType other) {
    if (!isSuperType(other)) return false;
    if (getNullability() == DfaNullability.UNKNOWN) {
      DfaNullability otherNullability = DfaNullability.fromDfType(other);
      return otherNullability != DfaNullability.NULL && otherNullability != DfaNullability.NULLABLE;
    }
    return true;
  }

  @NotNull
  @Override
  public DfType join(@NotNull DfType other) {
    if (isSuperType(other)) return this;
    if (other.isSuperType(this)) return other;
    if (!(other instanceof DfReferenceType)) return TOP;
    if (other instanceof DfNullConstantType || other instanceof DfEphemeralReferenceType) {
      return other.join(this);
    }
    DfReferenceType type = (DfReferenceType)other;
    TypeConstraint constraint = getConstraint().join(type.getConstraint());
    if (constraint == TypeConstraints.BOTTOM) {
      throw new AssertionError("Join failed: " + this + " | " + other);
    }
    DfaNullability nullability = getNullability().unite(type.getNullability());
    Mutability mutability = getMutability().join(type.getMutability());
    boolean locality = isLocal() && type.isLocal();
    SpecialField sf = Objects.equals(getSpecialField(), type.getSpecialField()) ? getSpecialField() : null;
    DfType sfType = sf == null ? BOTTOM : getSpecialFieldType().join(type.getSpecialFieldType());
    Set<Object> notValues = myNotValues;
    if (type instanceof DfGenericObjectType) {
      notValues = new HashSet<>(myNotValues);
      notValues.retainAll(((DfGenericObjectType)other).myNotValues);
    }
    if (type instanceof DfReferenceConstantType) {
      notValues = new HashSet<>(myNotValues);
      notValues.remove(((DfReferenceConstantType)type).getValue());
    }
    return new DfGenericObjectType(notValues, constraint, nullability, mutability, sf, sfType, locality);
  }

  @Override
  public @Nullable DfType tryJoinExactly(@NotNull DfType other) {
    if (isMergeable(other)) return this;
    if (other.isMergeable(this)) return other;
    if (!(other instanceof DfReferenceType)) return null;
    if (other instanceof DfNullConstantType) {
      if (mySpecialField != null) return null;
      return new DfGenericObjectType(myNotValues, getConstraint(), DfaNullability.NULL.unite(getNullability()),
                                     getMutability(), null, BOTTOM, isLocal());
    }
    if (other instanceof DfReferenceConstantType) {
      Object otherValue = ((DfReferenceConstantType)other).getValue();
      if (!myNotValues.contains(otherValue)) return null;
      if (mySpecialField != null) return null;
      Set<Object> notValues = new HashSet<>(myNotValues);
      notValues.remove(otherValue);
      TypeConstraint constraint = getConstraint().tryJoinExactly(((DfReferenceConstantType)other).getConstraint());
      return constraint == null ? null : new DfGenericObjectType(notValues, constraint, getNullability(),
                                                                 getMutability(), null, BOTTOM, isLocal());
    }
    if (other instanceof DfGenericObjectType) {
      DfGenericObjectType objectType = (DfGenericObjectType)other;
      Mutability otherMutability = objectType.getMutability();
      DfaNullability otherNullability = objectType.getNullability();
      Set<Object> otherNotValues = objectType.getRawNotValues();
      TypeConstraint otherConstraint = objectType.getConstraint();
      SpecialField otherSpecialField = objectType.getSpecialField();
      DfType otherSfType = objectType.getSpecialFieldType();
      boolean otherLocal = objectType.isLocal();
      final int MUTABILITY = 0x01;
      final int NULLABILITY = 0x02;
      final int NOT_VALUES = 0x04;
      final int CONSTRAINT = 0x08;
      final int SPECIAL_FIELD = 0x10;
      final int SF_TYPE = 0x20;
      final int LOCAL = 0x40;
      int bits = 0;
      if (otherMutability != myMutability) bits |= MUTABILITY;
      if (otherNullability != myNullability) bits |= NULLABILITY;
      if (!otherNotValues.equals(myNotValues)) bits |= NOT_VALUES;
      if (!otherConstraint.equals(myConstraint)) bits |= CONSTRAINT;
      if (otherSpecialField != mySpecialField) bits |= SPECIAL_FIELD;
      if (!otherSfType.equals(mySpecialFieldType)) bits |= SF_TYPE;
      if (otherLocal != myLocal) bits |= LOCAL;
      switch (bits) {
        case CONSTRAINT:
        case NULLABILITY | CONSTRAINT: {
          TypeConstraint constraint = otherConstraint.tryJoinExactly(myConstraint);
          return constraint == null ? null :
                 new DfGenericObjectType(myNotValues, constraint, myNullability.unite(otherNullability), myMutability,
                                         mySpecialField, mySpecialFieldType, myLocal);
        }
        case MUTABILITY:
          return new DfGenericObjectType(myNotValues, myConstraint, myNullability, myMutability.join(otherMutability),
                                         mySpecialField, mySpecialFieldType, myLocal);
        case NULLABILITY:
          return new DfGenericObjectType(myNotValues, myConstraint, myNullability.unite(otherNullability), myMutability,
                                         mySpecialField, mySpecialFieldType, myLocal);
        case NOT_VALUES: {
          Set<Object> notValues = new HashSet<>(myNotValues);
          notValues.retainAll(otherNotValues);
          return new DfGenericObjectType(notValues, myConstraint, myNullability, myMutability,
                                         mySpecialField, mySpecialFieldType, myLocal);
        }
        case SF_TYPE: {
          DfType sfType = otherSfType.tryJoinExactly(mySpecialFieldType);
          return sfType == null ? null :
                 new DfGenericObjectType(myNotValues, myConstraint, myNullability, myMutability,
                                         mySpecialField, sfType, myLocal);
        }
      }
    }
    return null;
  }

  @NotNull
  @Override
  public DfType meet(@NotNull DfType other) {
    if (other instanceof DfConstantType || other instanceof DfEphemeralReferenceType) {
      return other.meet(this);
    }
    if (isSuperType(other)) return other;
    if (other.isSuperType(this)) return this;
    if (!(other instanceof DfReferenceType)) return BOTTOM;
    DfReferenceType type = (DfReferenceType)other;
    TypeConstraint constraint = getConstraint().meet(type.getConstraint());
    if (constraint == TypeConstraints.BOTTOM) {
      return isSuperType(DfTypes.NULL) && other.isSuperType(DfTypes.NULL) ? DfTypes.NULL : BOTTOM;
    }
    DfaNullability nullability = getNullability().intersect(type.getNullability());
    if (nullability == null) return BOTTOM;
    Mutability mutability = getMutability().meet(type.getMutability());
    if (mutability == null) return BOTTOM;
    boolean locality = isLocal() || type.isLocal();
    SpecialField sf;
    DfType sfType;
    if (getSpecialField() == null) {
      sf = type.getSpecialField();
      sfType = type.getSpecialFieldType();
    }
    else if (type.getSpecialField() == null) {
      sf = getSpecialField();
      sfType = getSpecialFieldType();
    } else {
      sf = getSpecialField();
      if (sf != type.getSpecialField()) return BOTTOM;
      sfType = getSpecialFieldType().meet(type.getSpecialFieldType());
    }
    if (sf != null && sfType == BOTTOM) return BOTTOM;
    Set<Object> notValues = myNotValues;
    if (type instanceof DfGenericObjectType) {
      Set<Object> otherNotValues = ((DfGenericObjectType)other).myNotValues;
      if (otherNotValues.containsAll(myNotValues)) {
        notValues = otherNotValues;
      }
      else if (!myNotValues.containsAll(otherNotValues)) {
        notValues = new HashSet<>(myNotValues);
        notValues.addAll(otherNotValues);
        if (nullability == DfaNullability.NOT_NULL) {
          DfEphemeralReferenceType ephemeralValue = checkEphemeral(constraint, notValues);
          if (ephemeralValue != null) {
            return ephemeralValue;
          }
        }
      }
    }
    if (nullability == DfaNullability.NOT_NULL && constraint.isSingleton()) {
      return new DfReferenceConstantType(constraint, constraint, false);
    }
    return new DfGenericObjectType(notValues, constraint, nullability, mutability, sf, sfType, locality);
  }

  @Override
  public DfType widen() {
    DfType wideSpecialField = mySpecialFieldType.widen();
    if (!wideSpecialField.equals(mySpecialFieldType)) {
      return new DfGenericObjectType(myNotValues, myConstraint, myNullability, myMutability, mySpecialField, wideSpecialField, myLocal);
    }
    return this;
  }

  private static DfEphemeralReferenceType checkEphemeral(TypeConstraint constraint, Set<Object> notValues) {
    if (notValues.isEmpty()) return null;
    Object value = notValues.iterator().next();
    if (!(value instanceof PsiEnumConstant)) return null;
    PsiClass enumClass = ((PsiEnumConstant)value).getContainingClass();
    if (enumClass == null) return null;
    TypeConstraint enumType = TypeConstraints.instanceOf(
      JavaPsiFacade.getElementFactory(enumClass.getProject()).createType(enumClass));
    if (!enumType.equals(constraint)) return null;
    Set<PsiEnumConstant> allEnumConstants = StreamEx.of(enumClass.getFields()).select(PsiEnumConstant.class).toSet();
    if (notValues.size() != allEnumConstants.size()) return null;
    for (Object notValue : notValues) {
      if (!(notValue instanceof PsiEnumConstant)) return null;
      if (!allEnumConstants.remove(notValue)) return null;
    }
    if (allEnumConstants.isEmpty()) {
      return new DfEphemeralReferenceType(constraint);
    }
    return null;
  }

  @Override
  public @NotNull DfType meetRelation(@NotNull RelationType relationType,
                                      @NotNull DfType other) {
    if ((relationType == RelationType.EQ || relationType == RelationType.IS) && isLocal() && myConstraint.isComparedByEquals()) {
      return dropLocality().meetRelation(relationType, other);
    }
    return super.meetRelation(relationType, other);
  }

  @Override
  public @NotNull DfType fromRelation(@NotNull RelationType relationType) {
    if (relationType == RelationType.EQ || relationType == RelationType.IS) {
      DfReferenceType result = this;
      if (myConstraint.isComparedByEquals()) {
        result = result.dropLocality();
      }
      if (myNullability == DfaNullability.NULLABLE) {
        result = result.dropNullability();
      }
      return result;
    }
    if (relationType == RelationType.IS_NOT) {
      DfType negated = tryNegate();
      if (negated != null) {
        return negated;
      }
    }
    return DfTypes.OBJECT_OR_NULL;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DfGenericObjectType type = (DfGenericObjectType)o;
    return myLocal == type.myLocal &&
           myNullability == type.myNullability &&
           myMutability == type.myMutability &&
           mySpecialField == type.mySpecialField &&
           myConstraint.equals(type.myConstraint) &&
           mySpecialFieldType.equals(type.mySpecialFieldType) &&
           myNotValues.equals(type.myNotValues);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myConstraint, myNullability, myMutability, mySpecialField, mySpecialFieldType, myLocal, myNotValues);
  }

  @Override
  protected String renderValue(Object value) {
    return DfaPsiUtil.renderValue(value);
  }

  @Override
  public @NotNull String toString() {
    List<String> components = new ArrayList<>();
    if (myConstraint != TypeConstraints.TOP) {
      components.add(myConstraint.toString());
    }
    if (myNullability != DfaNullability.UNKNOWN) {
      components.add(myNullability.toString());
    }
    if (myMutability != Mutability.UNKNOWN) {
      components.add(myMutability.name());
    }
    if (myLocal) {
      components.add(JavaBundle.message("type.information.local.object"));
    }
    if (mySpecialField != null) {
      components.add(mySpecialField + "=" + mySpecialFieldType);
    }
    if (!myNotValues.isEmpty()) {
      components.add(super.toString());
    }
    return String.join(" ", components);
  }
}
