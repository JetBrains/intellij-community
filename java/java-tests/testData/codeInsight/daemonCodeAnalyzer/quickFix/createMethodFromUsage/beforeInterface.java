// "Create method 'test'" "true-preview"
public interface Test {

}

class Foo {
  void bar() {
    Test test = null;
    test.te<caret>st();
  }
}