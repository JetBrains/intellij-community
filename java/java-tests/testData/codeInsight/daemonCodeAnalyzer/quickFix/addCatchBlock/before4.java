// "Add Catch Clause(s)" "true"
class MyException1 extends Exception {}
class MyException2 extends Exception {}

class Test {
  void foo () throws MyException1, MyException2 {}

  void bar () {
    try {
      <caret>foo();
    } catch (MyException1 e) {
    }
  }
}