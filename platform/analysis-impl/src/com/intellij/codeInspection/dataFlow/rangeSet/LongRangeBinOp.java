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
    return switch (this) {
      case PLUS -> left.plus(right, lrType);
      case MINUS -> left.minus(right, lrType);
      case AND -> left.bitwiseAnd(right);
      case OR -> left.bitwiseOr(right, lrType);
      case XOR -> left.bitwiseXor(right, lrType);
      case MUL -> left.mul(right, lrType);
      case MOD -> left.mod(right);
      case DIV -> left.div(right, lrType);
      case SHL -> left.shiftLeft(right, lrType);
      case SHR -> left.shiftRight(right, lrType);
      case USHR -> left.unsignedShiftRight(right, lrType);
    };
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
    return switch (this) {
      case PLUS -> left.plusWiden(right, lrType);
      case MINUS -> {
        if (Long.valueOf(0).equals(left.getConstantValue())) {
          // Unary minus
          yield left.minus(right, lrType);
        }
        yield left.plusWiden(right.negate(lrType), lrType);
      }
      case MUL -> left.mulWiden(right, lrType);
      default -> eval(left, right, lrType);
    };
  }

  @Override
  public String toString() {
    return mySymbol;
  }

  public boolean isShift() {
    return this == SHL || this == SHR || this == USHR;
  }
}
