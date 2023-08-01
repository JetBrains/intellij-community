package com.siyeh.ipp.conditional.withIf;

class ConditionalInBinaryExpression {

  public String foo(int num) {
    return switch (0) {
      default -> num > 0 <caret>? "a" : "b";
    };
  }
}