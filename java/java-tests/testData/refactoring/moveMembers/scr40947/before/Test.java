class A {
  static void foo() {
    A.bar(); // note redundant "A" qualifier
  }
  static void bar() {}
}

class Test {

}