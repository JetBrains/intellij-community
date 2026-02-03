package com.siyeh.ipp.parentheses;

class ArrayAccessExpression {
  private static boolean placeEqualsLastArg(Object place, Object[] args) {
    return args.length > 0 && place.equals(args[(ar<caret>gs.length - 1)]);// here are unnecessary parentheses inside args[...]
  }
}