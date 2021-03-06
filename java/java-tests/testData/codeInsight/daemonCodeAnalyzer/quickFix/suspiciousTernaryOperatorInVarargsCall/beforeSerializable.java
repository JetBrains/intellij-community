// "Replace with 'new Serializable[]{b}'" "true"

import java.io.Serializable;

class Test {
  static void bar(boolean flag) {
    Serializable[] a = {1, 2};
    Serializable b = "hello";
    foo(0, flag ? a : b<caret>);
  }
  static void foo(int x, Serializable... xs) {
  }
}