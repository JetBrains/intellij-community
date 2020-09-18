// "Replace with 'new Serializable[]{b}'" "true"

import java.io.Serializable;

class Test {
  public static void main(String[] args) {
    Serializable[] a = {1, 2};
    Serializable b = "hello";
    foo(0, a);
    foo(0, b);
    for (boolean flag : new boolean[]{true, false}) {
      foo(0, flag ? a : new Serializable[]{b});
      foo(0, 1, flag ? a : b);
    }
  }
  static void foo(int x, Serializable... xs) {
  }
}