// "Make 'foo' return 'int'" "true"
abstract class A {
  private int condition(boolean flag) {
    return flag ? foo() : bar();
  }

  abstract int foo();
  abstract C bar();

  interface B {}
  interface C extends B {}

}
