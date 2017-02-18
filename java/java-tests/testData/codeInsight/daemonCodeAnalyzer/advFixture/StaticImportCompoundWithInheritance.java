package a;

import static a.A.foo;

interface B extends A {
  static void bar() {
    foo("");
  }
}