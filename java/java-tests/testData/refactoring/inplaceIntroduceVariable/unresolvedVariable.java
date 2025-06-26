
class Test {
  void f(Object o) {
    System.out.println(((SomeUnresolved)o).doSomet<caret>hing());
  }

  class SomeUnresolved{
    Test doSomething() {}
  }
}

