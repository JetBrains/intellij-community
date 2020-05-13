// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.value;

import com.intellij.psi.JavaTokenType;
import com.intellij.psi.tree.IElementType;
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
    switch (this) {
      case LE:
        return other == LT || other == EQ;
      case GE:
        return other == GT || other == EQ;
      case NE:
        return other == LT || other == GT;
      default:
        return false;
    }
  }

  @NotNull
  public RelationType getNegated() {
    switch (this) {
      case LE:
        return GT;
      case LT:
        return GE;
      case GE:
        return LT;
      case GT:
        return LE;
      case EQ:
        return NE;
      case NE:
        return EQ;
      case IS:
        return IS_NOT;
      case IS_NOT:
        return IS;
    }
    throw new InternalError("Unexpected enum value: " + this);
  }

  @Nullable
  public RelationType getFlipped() {
    switch (this) {
      case LE:
        return GE;
      case LT:
        return GT;
      case GE:
        return LE;
      case GT:
        return LT;
      case EQ:
      case NE:
        return this;
      default:
        return null;
    }
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

  @Nullable
  public static RelationType fromElementType(IElementType type) {
    if(JavaTokenType.EQEQ.equals(type)) {
      return EQ;
    }
    if(JavaTokenType.NE.equals(type)) {
      return NE;
    }
    if(JavaTokenType.LT.equals(type)) {
      return LT;
    }
    if(JavaTokenType.GT.equals(type)) {
      return GT;
    }
    if(JavaTokenType.LE.equals(type)) {
      return LE;
    }
    if(JavaTokenType.GE.equals(type)) {
      return GE;
    }
    if(JavaTokenType.INSTANCEOF_KEYWORD.equals(type)) {
      return IS;
    }
    return null;
  }

  public static RelationType equivalence(boolean equal) {
    return equal ? EQ : NE;
  }
}
