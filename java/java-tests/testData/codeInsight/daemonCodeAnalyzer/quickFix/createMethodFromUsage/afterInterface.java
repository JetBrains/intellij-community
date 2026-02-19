// "Create method 'test'" "true-preview"
public interface Test {

    void test();
}

class Foo {
  void bar() {
    Test test = null;
    test.test();
  }
}