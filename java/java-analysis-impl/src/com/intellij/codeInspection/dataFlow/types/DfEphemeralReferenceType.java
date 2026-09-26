// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.types;

import com.intellij.codeInspection.dataFlow.DfaNullability;
import com.intellij.codeInspection.dataFlow.Mutability;
import com.intellij.codeInspection.dataFlow.TypeConstraint;
import com.intellij.codeInspection.dataFlow.TypeConstraints;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * A reference value that is impossible with currently available source code but
 * could appear if separate compilation takes place. A common example of ephemeral value
 * is a constant of enum type that doesn't equal to any existing constant.
 * <p>
 * When ephemeral value is stored in the memory state variable we assume that the whole
 * memory state is ephemeral.
 *
 * @see DfaMemoryState#isEphemeral()
 */
public class DfEphemeralReferenceType implements DfEphemeralType, DfReferenceType {
  private final @NotNull TypeConstraint myTypeConstraint;

  DfEphemeralReferenceType(@NotNull TypeConstraint constraint) {
    myTypeConstraint = constraint;
  }

  @Override
  public @NotNull DfaNullability getNullability() {
    return DfaNullability.NOT_NULL;
  }

  @Override
  public @NotNull TypeConstraint getConstraint() {
    return myTypeConstraint;
  }

  @Override
  public @NotNull DfReferenceType dropNullability() {
    return this;
  }

  @Override
  public @NotNull DfReferenceType convert(TypeConstraints.@NotNull TypeConstraintFactory factory) {
    return new DfEphemeralReferenceType(myTypeConstraint.convert(factory));
  }

  @Override
  public @NotNull DfReferenceType dropTypeConstraint() {
    return DfTypes.NOT_NULL_OBJECT;
  }

  @Override
  public boolean isSuperType(@NotNull DfType other) {
    if (other == DfType.BOTTOM) return true;
    if (other instanceof DfEphemeralReferenceType) {
      return myTypeConstraint.isSuperConstraintOf(((DfEphemeralReferenceType)other).myTypeConstraint);
    }
    return false;
  }

  @Override
  public @NotNull DfType join(@NotNull DfType other) {
    if (other == DfType.BOTTOM) return this;
    if (other == DfType.TOP || !(other instanceof DfReferenceType)) return DfType.TOP;
    TypeConstraint otherConstraint = ((DfReferenceType)other).getConstraint();
    TypeConstraint constraint = myTypeConstraint.join(otherConstraint);
    if (other instanceof DfEphemeralReferenceType) {
      return constraint == myTypeConstraint ? this :
             constraint == otherConstraint ? other :
             constraint == TypeConstraints.TOP ? DfTypes.NOT_NULL_OBJECT :
             new DfEphemeralReferenceType(constraint);
    }
    Set<Object> notValues = other instanceof DfGenericObjectType ? ((DfGenericObjectType)other).getRawNotValues() : Set.of();
    return new DfGenericObjectType(notValues, constraint, ((DfReferenceType)other).getNullability(),
                                   Mutability.UNKNOWN, null, DfType.BOTTOM, false);
  }

  @Override
  public @Nullable DfType tryJoinExactly(@NotNull DfType other) {
    if (other == DfType.BOTTOM) return this;
    if (other == DfType.TOP) return other;
    if (other instanceof DfEphemeralReferenceType) {
      TypeConstraint otherConstraint = ((DfEphemeralReferenceType)other).getConstraint();
      TypeConstraint constraint = myTypeConstraint.tryJoinExactly(otherConstraint);
      return constraint == null ? null :
             constraint == myTypeConstraint ? this :
             constraint == otherConstraint ? other :
             new DfEphemeralReferenceType(constraint);
    }
    return null;
  }

  @Override
  public @NotNull DfType meet(@NotNull DfType other) {
    if (other == DfType.TOP) return this;
    if (other == DfType.BOTTOM) return other;
    if (other instanceof DfEphemeralReferenceType ||
        other instanceof DfGenericObjectType) {
      TypeConstraint otherConstraint = ((DfReferenceType)other).getConstraint();
      TypeConstraint constraint = myTypeConstraint.meet(otherConstraint);
      return constraint == myTypeConstraint ? this :
             constraint == otherConstraint ? other :
             constraint == TypeConstraints.BOTTOM ? DfType.BOTTOM :
             new DfEphemeralReferenceType(constraint);
    }
    return DfType.BOTTOM;
  }

  @Override
  public boolean equals(Object o) {
    return this == o || o instanceof DfEphemeralReferenceType && myTypeConstraint.equals(((DfEphemeralReferenceType)o).myTypeConstraint);
  }

  @Override
  public int hashCode() {
    return myTypeConstraint.hashCode();
  }

  @Override
  public @NotNull String toString() {
    return "ephemeral " + getConstraint();
  }
}
