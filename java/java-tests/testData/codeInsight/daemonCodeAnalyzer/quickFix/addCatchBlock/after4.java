// "Add 'catch' clause(s)" "true"
class MyException1 extends Exception {}
class MyException2 extends Exception {}

class Test {
  void foo () throws MyException1, MyException2 {}

  void bar () {
    try {
      foo();
    } catch (MyException1 e) {
    } catch (MyException2 e) {
        <caret><selection>throw new RuntimeException(e);</selection>
    }
  }
}