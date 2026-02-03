package com.siyeh.ipp.conditional.withIf;

class ConditionalInBinaryExpression {

  public String foo(int num) {
      if (num > 0)<caret> {
          throw new RuntimeException();
      }
      throw new IllegalStateException();
  }
}