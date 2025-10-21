
class Test {
  void f(Object o) {
      var o = ((SomeUnresolved) o).doSomething();
      System.out.println(o);
  }

  class SomeUnresolved{
    Test doSomething() {}
  }
}

