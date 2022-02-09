// "Make 'condition' return 'Test.Child2' or predecessor" "true"
abstract class Test {
  private void condition(boolean flag) {
    return flag ? foo() : b<caret>ar();
  }

  abstract void foo();
  abstract Child2 bar();

  interface Base {}
  interface Child1 extends Base {}
  interface Child2 extends Base {}
}