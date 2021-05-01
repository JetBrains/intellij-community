// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.types;

import com.intellij.codeInspection.dataFlow.value.RelationType;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Set;

/**
 * Represents a domain of possible values within data flow analysis
 */
public interface DfType {

  /**
   * A type that contains every possible value supported by the type system
   */
  DfType TOP = new DfType() {
    @Override
    public boolean isSuperType(@NotNull DfType other) {
      return true;
    }

    @Override
    public @NotNull DfType join(@NotNull DfType other) {
      return this;
    }

    @Override
    public @NotNull DfType meet(@NotNull DfType other) {
      return other;
    }

    @Override
    public @NotNull DfType tryNegate() {
      return BOTTOM;
    }

    @Override
    public @NotNull DfType fromRelation(@NotNull RelationType relationType) {
      return this;
    }

    @Override
    public int hashCode() {
      return 1254215;
    }

    @Override
    public @NotNull String toString() {
      return "TOP";
    }
  };
  /**
   * A type that contains no values
   */
  DfType BOTTOM = new DfType() {
    @Override
    public boolean isSuperType(@NotNull DfType other) {
      return other == this;
    }

    @Override
    public @NotNull DfType join(@NotNull DfType other) {
      return other;
    }

    @Override
    public @NotNull DfType meet(@NotNull DfType other) {
      return this;
    }

    @Override
    public @NotNull DfType fromRelation(@NotNull RelationType relationType) {
      return this;
    }

    @Override
    public @NotNull DfType tryNegate() {
      return TOP;
    }

    @Override
    public int hashCode() {
      return 67532141;
    }

    @Override
    public @NotNull String toString() {
      return "BOTTOM";
    }
  };
  /**
   * A special value that represents a contract failure after method return (the control flow should immediately proceed
   * with exception handling). This value is like a constant but its type doesn't correspond to any JVM type.
   */
  DfType FAIL = new DfConstantType<>(ObjectUtils.sentinel("FAIL")) {
    @Override
    public @NotNull DfType join(@NotNull DfType other) {
      return other == this ? this : TOP;
    }

    @Override
    public @NotNull DfType meet(@NotNull DfType other) {
      return other == this ? this : BOTTOM;
    }

    @Override
    public @NotNull DfType tryNegate() {
      return NOT_FAIL;
    }

    @Override
    public @NotNull DfType fromRelation(@NotNull RelationType relationType) {
      return relationType == RelationType.EQ ? this :
             relationType == RelationType.NE ? NOT_FAIL :
             BOTTOM;
    }

    @Override
    public int hashCode() {
      return 5362412;
    }
  };
  /**
   * Anything but a FAIL value
   */
  DfType NOT_FAIL = new DfAntiConstantType<>(Set.of(Objects.requireNonNull(FAIL.getConstantOfType(Object.class)))) {
    @Override
    public boolean isSuperType(@NotNull DfType other) {
      return other != TOP && other != FAIL;
    }

    @Override
    public @NotNull DfType join(@NotNull DfType other) {
      return other == FAIL ? TOP : this;
    }

    @Override
    public @NotNull DfType meet(@NotNull DfType other) {
      return other == FAIL ? BOTTOM : this;
    }

    @Override
    public @NotNull DfType tryNegate() {
      return FAIL;
    }

    @Override
    public int hashCode() {
      return 23145416;
    }
  };

  /**
   * Checks whether this type is the supertype of the supplied type, i.e. every value from the other type belongs to this type as well.
   * if A.isSuperType(B) then A.join(B) is A and A.meet(B) is B.
   *
   * @param other other type
   * @return true if this type is the supertype of other.
   */
  boolean isSuperType(@NotNull DfType other);

  /**
   * @param constant
   * @return true given constant value may be contained by this supertype
   */
  default boolean containsConstant(@NotNull DfConstantType<?> constant) {
    return isSuperType(constant);
  }

  default boolean isMergeable(@NotNull DfType other) {
    return isSuperType(other);
  }

  /**
   * Return the most specific type that contains all values from this type and from other type.
   * @param other type to join
   * @return the result of the join operation
   */
  @NotNull
  DfType join(@NotNull DfType other);

  /**
   * Returns the least specific type that contains all values that belong both to this type and to other type.
   * @param other type to meet
   * @return the result of the meet operation.
   */
  @NotNull
  DfType meet(@NotNull DfType other);

  @NotNull
  default DfType fromRelation(@NotNull RelationType relationType) {
    return relationType == RelationType.EQ ? this : TOP;
  }

  /**
   * @return the widened version of this type; should be called on back-branches.
   */
  default DfType widen() {
    return this;
  }

  /**
   * @return a type that contains all the values of the corresponding JVM type except the values of given type;
   * may return null if the corresponding type is not supported by our type system.
   */
  @Nullable
  default DfType tryNegate() {
    return null;
  }

  /**
   * @param constant constant to compare to
   * @return true if this type represents a constant with given value
   */
  default boolean isConst(@Nullable Object constant) {
    return false;
  }

  /**
   * @param clazz desired constant class
   * @param <C> type of the constant
   * @return the constant of given type; null if this type does not represent a constant of supplied type.
   */
  default <C> @Nullable C getConstantOfType(@NotNull Class<C> clazz) {
    return null;
  }

  /**
   * Correct the inherent variable type when concrete variable state is flushed
   * due to unknown code execution. This could be useful to handle non-strict 
   * properties like nullability (e.g. if unstable nullable variable was checked for null,
   * then unknown code was executed, then the variable should not probably reset to nullable)
   * 
   * @param typeBeforeFlush type the variable had before flush
   * @return corrected inherent variable type; return this if no correction is necessary.
   */
  default DfType correctTypeOnFlush(DfType typeBeforeFlush) {
    return this;
  }

  /**
   * Correct the variable type that is passed from outer context to the closure 
   * (e.g. drop the locality flag).
   * 
   * @return corrected variable type; return this if no correction is necessary.
   */
  default DfType correctForClosure() {
    return this;
  }

  /**
   * @return human-readable representation of this DfType, could be localized
   */
  @Override @NlsSafe @NotNull String toString();
}
