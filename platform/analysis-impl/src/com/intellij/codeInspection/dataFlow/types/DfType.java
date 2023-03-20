// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.types;

import com.intellij.codeInspection.dataFlow.value.DerivedVariableDescriptor;
import com.intellij.codeInspection.dataFlow.value.RelationType;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
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
    public @NotNull DfType tryJoinExactly(@NotNull DfType other) { return this; }

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
    public @NotNull DfType tryJoinExactly(@NotNull DfType other) { return other; }

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
    public @Nullable DfType tryJoinExactly(@NotNull DfType other) { return other == this ? this : other == NOT_FAIL ? TOP : null; }

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
    public @NotNull DfType tryJoinExactly(@NotNull DfType other) { return join(other); }

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
   * @return true if this type contains only local objects (not leaked from the current context to unknown places)
   * In particular, this means that the values of these types are never {@linkplain #mayAlias(DfType) aliased} 
   * to any other values.
   */
  default boolean isLocal() {
    return false;
  }

  /**
   * @param otherType other type
   * @return true if values qualified by this value might be affected by values qualified by otherType.
   * For example, if both this and otherType are pointer types that may refer to the same memory location.
   */
  default boolean mayAlias(DfType otherType) {
    return false;
  }

  /**
   * @return true if values immediately qualified by this type, never change, 
   * as long as qualifier never changes.
   */
  default boolean isImmutableQualifier() {
    return false;
  }

  /**
   * Checks whether processing the other type is not necessary to get the same analysis result if some value
   * has either this or other state at the same code location. {@code a.isMergeable(b)} implies 
   * {@code a.isSuperType(b)}. In most cases, these methods are equivalent but the difference may appear in
   * processing non-strict properties like nullability. E.g. (nullability == unknown) is supertype of
   * (nullability == null) but it's not mergeable, as skipping (nullability == null) processing will remove
   * "Possible NPE" warning.
   * 
   * @param other other type
   * @return true if processing the other type is not necessary to get the same analysis result if some value
   * has either this or other state at the same code location.
   */
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
   * Return the type that contains all values from this type and from other type and no other values.
   * @param other type to join
   * @return the result of the join operation; null if exact join cannot be represented
   */
  @Nullable DfType tryJoinExactly(@NotNull DfType other);

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
   * @return true if equivalence relation on this type is not standard. That is,
   * for constant values belonging to this type, {@code a.meetRelation(EQ, b) != a.equals(b)}
   */
  default boolean hasNonStandardEquivalence() {
    return false;
  }
  
  /**
   * Narrows this value to the set of values that satisfies given relation
   * 
   * @param relationType relation applied
   * @param other other operand (e.g., for {@code a < b} relation, this object is {@code a},
   *              other is {@code b} and relationType is {@code <}.
   * @return narrowed type containing only values that satisfy the relation 
   * (may contain some more values if the exact type cannot be represented).
   * For any {@code meetRelation} arguments, {@code this.isSuperType(this.meetRelation(...))} is true.
   */
  default @NotNull DfType meetRelation(@NotNull RelationType relationType, @NotNull DfType other) {
    return meet(other.fromRelation(relationType));
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
   * Correct the type after applying the relation depending on whether the relation was applied successfully.
   * May be necessary to handle weird relation semantics, like with NaN in some languages.
   * 
   * @param relation relation applied
   * @param result if true then the relation was applied successfully. E.g., for {@code a > b},
   *               result is true for {@code a > b} state and false for {@code a < b} and for {@code a == b} states.
   * @return corrected type; return this if no correction is necessary
   */
  default @NotNull DfType correctForRelationResult(@NotNull RelationType relation, boolean result) {
    return this;
  }

  /**
   * @return basic type for this type. Some types could represent several derived variables. For example,
   * the type "string of length 3" could be decomposed to basic type "string" and derived variable "length"
   * with value "3".
   */
  default @NotNull DfType getBasicType() {
    return this;
  }

  /**
   * @return list of possible derived variables that could be recorded inside this type.
   * E.g. the type "string of length 3" records the derived variable "string length" inside.
   */
  default @NotNull List<@NotNull DerivedVariableDescriptor> getDerivedVariables() {
    return List.of();
  }

  /**
   * @return values of all derived variables stored inside this type
   */
  default @NotNull Map<@NotNull DerivedVariableDescriptor, @NotNull DfType> getDerivedValues() {
    return Map.of();
  }

  /**
   * @return human-readable representation of this DfType, could be localized
   */
  @Override @NlsSafe @NotNull String toString();
}
