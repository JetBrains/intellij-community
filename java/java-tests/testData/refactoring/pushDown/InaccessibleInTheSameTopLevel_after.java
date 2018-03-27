
class Outer {

  class A {
    private void foo() {}
    private int i;

  }

  class B extends A {
      final void test() {
        foo();
        System.out.println(i);
      }
  }
}