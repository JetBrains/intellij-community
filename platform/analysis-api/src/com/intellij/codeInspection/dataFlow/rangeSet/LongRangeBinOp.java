// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.rangeSet;

import org.jetbrains.annotations.NotNull;

/**
 * Supported binary operations over long ranges.
 */
public enum LongRangeBinOp {
  PLUS("+"), MINUS("-"), AND("&"), OR("|"), XOR("^"), MUL("*"),
  MOD("%"), DIV("/"), SHL("<<"), SHR(">>"), USHR(">>>");

  final String mySymbol;

  LongRangeBinOp(String symbol) {
    mySymbol = symbol;
  }

  /**
   * Performs a binary operation on ranges.
   *
   * @param left a left-hand operand
   * @param right  a right-hand operand
   * @param isLong true if operation should be performed on long types (otherwise int is assumed)
   * @return the resulting LongRangeSet which covers possible results of the operation (probably including some more elements).
   */
  public @NotNull LongRangeSet eval(@NotNull LongRangeSet left, @NotNull LongRangeSet right, boolean isLong) {
    switch (this) {
      case PLUS:
        return left.plus(right, isLong);
      case MINUS:
        return left.minus(right, isLong);
      case AND:
        return left.bitwiseAnd(right);
      case OR:
        return left.bitwiseOr(right, isLong);
      case XOR:
        return left.bitwiseXor(right, isLong);
      case MUL:
        return left.mul(right, isLong);
      case MOD:
        return left.mod(right);
      case DIV:
        return left.div(right, isLong);
      case SHL:
        return left.shiftLeft(right, isLong);
      case SHR:
        return left.shiftRight(right, isLong);
      case USHR:
        return left.unsignedShiftRight(right, isLong);
      default:
        throw new IllegalStateException("Unexpected value: " + this);
    }
  }

  /**
   * Performs a binary operation on ranges with possible widening, so that if operation is performed repeatedly
   * it will eventually converge.
   *
   * @param left a left-hand operand
   * @param right  a right-hand operand
   * @param isLong true if operation should be performed on long types (otherwise int is assumed)
   * @return the resulting LongRangeSet which covers possible results of the operation (probably including some more elements).
   */
  public @NotNull LongRangeSet evalWide(@NotNull LongRangeSet left, @NotNull LongRangeSet right, boolean isLong) {
    switch (this) {
      case PLUS:
        return left.plusWiden(right, isLong);
      case MINUS:
        if (Long.valueOf(0).equals(left.getConstantValue())) {
          // Unary minus
          return left.minus(right, isLong);
        }
        return left.plusWiden(right.negate(isLong), isLong);
      case MUL:
        return left.mulWiden(right, isLong);
      default:
        return eval(left, right, isLong);
    }
  }

  @Override
  public String toString() {
    return mySymbol;
  }

  public boolean isShift() {
    return this == SHL || this == SHR || this == USHR;
  }
}
