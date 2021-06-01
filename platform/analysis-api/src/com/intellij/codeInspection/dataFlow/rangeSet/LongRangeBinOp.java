// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.rangeSet;

import org.jetbrains.annotations.NotNull;

/**
 * Supported binary operations over long ranges.
 */
public enum LongRangeBinOp {
  PLUS("+"), MINUS("-"), AND("&"), OR("|"), XOR("^"), MUL("*"),
  MOD("%"), DIV("/"), SHL("<<"), SHR(">>"), USHR(">>>");

  private final String mySymbol;

  LongRangeBinOp(String symbol) {
    mySymbol = symbol;
  }

  /**
   * Performs a binary operation on ranges.
   *
   * @param left a left-hand operand
   * @param right  a right-hand operand
   * @param lrType type to use for computation
   * @return the resulting LongRangeSet which covers possible results of the operation (probably including some more elements).
   */
  public @NotNull LongRangeSet eval(@NotNull LongRangeSet left, @NotNull LongRangeSet right, LongRangeType lrType) {
    switch (this) {
      case PLUS:
        return left.plus(right, lrType);
      case MINUS:
        return left.minus(right, lrType);
      case AND:
        return left.bitwiseAnd(right);
      case OR:
        return left.bitwiseOr(right, lrType);
      case XOR:
        return left.bitwiseXor(right, lrType);
      case MUL:
        return left.mul(right, lrType);
      case MOD:
        return left.mod(right);
      case DIV:
        return left.div(right, lrType);
      case SHL:
        return left.shiftLeft(right, lrType);
      case SHR:
        return left.shiftRight(right, lrType);
      case USHR:
        return left.unsignedShiftRight(right, lrType);
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
   * @param lrType type to use for computation
   * @return the resulting LongRangeSet which covers possible results of the operation (probably including some more elements).
   */
  public @NotNull LongRangeSet evalWide(@NotNull LongRangeSet left, @NotNull LongRangeSet right, LongRangeType lrType) {
    switch (this) {
      case PLUS:
        return left.plusWiden(right, lrType);
      case MINUS:
        if (Long.valueOf(0).equals(left.getConstantValue())) {
          // Unary minus
          return left.minus(right, lrType);
        }
        return left.plusWiden(right.negate(lrType), lrType);
      case MUL:
        return left.mulWiden(right, lrType);
      default:
        return eval(left, right, lrType);
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
