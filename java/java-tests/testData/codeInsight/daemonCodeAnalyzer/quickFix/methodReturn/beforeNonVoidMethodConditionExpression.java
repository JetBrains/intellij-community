// "Make 'foo' return 'int'" "true"
abstract class A {
  private int condition(boolean flag) {
    return flag ? foo()<caret> : bar();
  }

  abstract B foo();
  abstract C bar();

  interface B {}
  interface C extends B {}

}
