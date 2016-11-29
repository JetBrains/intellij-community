class Foo{
  Bar bar;

  void doSomething() {
    bar.doSom<caret>ething();
  }

  class Bar {
    public void doSomething() {

    }
  }
}