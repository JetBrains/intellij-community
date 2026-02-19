package com.siyeh.igtest.controlflow.overly_complex_boolean_expression;

public class OverlyComplexBooleanExpression {

  boolean x(boolean b) {
    System.out.println();
    return <warning descr="Overly complex boolean expression (4 terms)">b && b || b && b</warning>;
  }

  boolean ignore(boolean b) {
    System.out.println();
    return b || b || b || b;
  }

}