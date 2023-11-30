package com.siyeh.igtest.style.simplifiable_if_statement;

public class IfMayBeConditional {

    void foo(int a, int b) {
        int c = 0;
        <warning descr="If statement can be replaced with '?:'">if</warning> (a < b) { c += a - b; } else { c -= b; }
        if (a < b) { c += a - b; } else { c *= b; }
    }

    void foo2(int a, int b) {
        int c = 0;
        <warning descr="If statement can be replaced with '?:'">if</warning> (a < b) { c += a - b; } else { c += b; }
    }

    void foo3(int i, StringBuilder sb) {
        <warning descr="If statement can be replaced with '?:'">if</warning> (i == 0) {
            sb.append("type.getConstructor()", 0, 1);
        }
        else {
            sb.append("DescriptorUtils.getFQName(cd)",0, 1);
        }
    }

    int foo4(int a, int b) {
        <warning descr="If statement can be replaced with '?:'">if</warning> (a < b) return a;
        else return b;
    }

    int foo5(int a, int b, int c) {
        if (a < b) {
            return a;
        } else {
            return b < c ? b : c;
        }
    }

    void foo6(int a, int b, int c) {
      int i;
      if (a < b) {
          i = a;
      } else {
          i = b < c ? b : c;
      }
    }

    void largeIf(boolean a, boolean b, boolean c) {
        final String value;
        if (a) {
            value = "a";
        } else if (b) {
            value = "b";
        } else if (c) {
            value = "c";
        } else {
            value = "d";
        }
    }

  void x() {
    String nullable = null;
    String a = nullable;
    if (a == null) {
      a = nullable(2);
    }
  }

  private String nullable(int i) {
    return null;
  }
}
