package com.siyeh.igtest.classmetrics.class_complexity;

public class <warning descr="Overly complex class 'ClassComplexity' (cyclomatic complexity = 132)">ClassComplexity</warning> {
  static {
    boolean a, b, c, d;
    a = b = c = d = true;
    if (a && b || c && d || a && b || c && d) {}
    if (a && b || c && d || a && b || c && d) {}
    if (a && b || c && d || a && b || c && d) {}
    if (a && b || c && d || a && b || c && d) {}
  }

  void f(boolean a, boolean b, boolean c, boolean d) {
    if (a && b || c && d || a && b || c && d) {}
    if (a && b || c && d || a && b || c && d) {}
    if (a && b || c && d || a && b || c && d) {}
    if (a && b || c && d || a && b || c && d) {}
  }

  void g(boolean a, boolean b, boolean c, boolean d) {
    if (a && b || c && d || a && b || c && d) {}
    if (a && b || c && d || a && b || c && d) {}
    if (a && b || c && d || a && b || c && d) {}
    if (a && b || c && d || a && b || c && d) {}
  }

  void h(boolean a, boolean b, boolean c, boolean d) {
    if (a && b || c && d || a && b || c && d) {}
    if (a && b || c && d || a && b || c && d) {}
    if (a && b || c && d || a && b || c && d) {}
    if (a && b || c && d || a && b || c && d) {}
  }
}
