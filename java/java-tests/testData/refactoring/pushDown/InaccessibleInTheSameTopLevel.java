
class Outer {

  class A {
    private void foo() {}
    private int i;

    final void t<caret>est() {
      foo();
      System.out.println(i);
    }
  }

  class B extends A {}
}