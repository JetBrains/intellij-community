// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.types;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a domain of possible values within data flow analysis
 */
public interface DfType {

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
  
  @Override @NlsSafe
  String toString();
}
