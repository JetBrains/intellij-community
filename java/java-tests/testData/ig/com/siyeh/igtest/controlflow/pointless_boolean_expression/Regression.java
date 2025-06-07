class Regression {

  void foo() {
    Regression bruh = new Regression();
    someMethod(bruh.equals<error descr="Expected 1 argument but found 0">()</error>); // this code is wrong on purpose (e.g. it's being edited by the developer)
  }

  static void someMethod(boolean a) { }
}
