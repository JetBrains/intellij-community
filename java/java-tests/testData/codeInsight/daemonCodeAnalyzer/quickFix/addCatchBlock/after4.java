// "Add Catch Clause(s)" "true"
class MyException1 extends Exception {}
class MyException2 extends Exception {}

class Test {
  void foo () throws MyException1, MyException2 {}

  void bar () {
    try {
      foo();
    } catch (MyException1 e) {
    } catch (MyException2 myException2) {
        <caret><selection>myException2.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.</selection>
    }
  }
}