// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.value;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public enum RelationType {
  LE("<="), LT("<"), GE(">="), GT(">"), EQ("=="), NE("!="),
  /**
   * Value on the left belongs to the class of values defined on the right.
   * Currently used to represent:
   * - instanceof (DfaValue IS DfaTypeValue)
   * - optional presense (DfaValue IS DfaOptionalValue)
   */
  IS("is"),
  /**
   * Value on the left does not belong to the class of values defined on the right (opposite to IS).
   */
  IS_NOT("isn't");

  private final String myName;

  RelationType(String name) {
    myName = name;
  }

  /**
   * @param other other relation to meet
   * @return result of meet operation: the relation that is a sub-relation of both this and other;
   * null if result is bottom
   */
  public @Nullable RelationType meet(@NotNull RelationType other) {
    if (isSubRelation(other)) return other;
    if (other.isSubRelation(this)) return this;
    if (this == NE && other == LE || this == LE && other == NE) return LT;
    if (this == NE && other == GE || this == GE && other == NE) return GT;
    if (this == LE && other == GE || this == GE && other == LE) return EQ;
    return null;
  }

  public boolean isSubRelation(RelationType other) {
    if (other == this) return true;
    return switch (this) {
      case LE -> other == LT || other == EQ;
      case GE -> other == GT || other == EQ;
      case NE -> other == LT || other == GT;
      default -> false;
    };
  }

  @NotNull
  public RelationType getNegated() {
    return switch (this) {
      case LE -> GT;
      case LT -> GE;
      case GE -> LT;
      case GT -> LE;
      case EQ -> NE;
      case NE -> EQ;
      case IS -> IS_NOT;
      case IS_NOT -> IS;
    };
  }

  @Nullable
  public RelationType getFlipped() {
    return switch (this) {
      case LE -> GE;
      case LT -> GT;
      case GE -> LE;
      case GT -> LT;
      case EQ, NE -> this;
      default -> null;
    };
  }

  /**
   * @return true if this relation is >, >=, <, != or <=
   */
  public boolean isInequality() {
    return this == LE || this == GE || this == LT || this == GT || this == NE;
  }

  @Override
  public String toString() {
    return myName;
  }

  public static RelationType equivalence(boolean equal) {
    return equal ? EQ : NE;
  }
}
