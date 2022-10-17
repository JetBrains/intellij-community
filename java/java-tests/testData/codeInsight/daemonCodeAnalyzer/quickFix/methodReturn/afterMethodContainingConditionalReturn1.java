// "Make 'condition()' return 'Test.Base' or predecessor" "true-preview"
abstract class Test {
  private Base condition(boolean flag) {
    return flag ? foo() : bar();
  }

  abstract Child1 foo();
  abstract Child2 bar();

  interface Base {}
  interface Child1 extends Base {}
  interface Child2 extends Base {}
}
