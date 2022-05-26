// "Make 'foo' return 'void'" "false"
abstract class A {
  private void condition(boolean flag) {
    return flag ? foo()<caret> : bar();
  }

  abstract B foo();
  abstract C bar();

  interface B {}
  interface C extends B {}

}
