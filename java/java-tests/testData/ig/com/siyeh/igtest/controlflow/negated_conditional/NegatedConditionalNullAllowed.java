// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.igtest.controlflow.negated_conditional;

class NegatedConditionalNullAllowed {

  String valueOf(Object obj) {
    return obj != null ? obj.toString() : "null";
  }

  String emptyStr(String str, String def) {
    return str == null ? def : str;
  }

  String getUnequal(String a, String b) {
    return <warning descr="Conditional expression with negated condition">a != b</warning> ? "unequal" : "equal";
  }

  String getEqual(String a, String b) {
    return a == b ? "equal" : "unequal";
  }

  String getOdd(int num) {
    return <warning descr="Conditional expression with negated condition">num % 2 != 0</warning> ? "odd" : "even";
  }

  String getEven(int num) {
    return num % 2 == 0 ? "even" : "odd";
  }

  String getUnequal(int a, int b) {
    return <warning descr="Conditional expression with negated condition">a != b</warning> ? "unequal" : "equal";
  }

  String getEqual(int a, int b) {
    return a == b ? "equal" : "unequal";
  }
}