// "Make 'condition' return 'java.lang.Object'" "false"
abstract class Test {
  private Child2 condition(boolean flag) {
    return flag ? foo<caret>() : bar();
  }

  abstract void foo();
  abstract Child2 bar();

  interface Base {}
  interface Child1 extends Base {}
  interface Child2 extends Base {}
}