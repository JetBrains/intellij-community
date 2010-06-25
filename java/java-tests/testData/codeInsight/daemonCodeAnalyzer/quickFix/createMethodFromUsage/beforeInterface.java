// "Create Method 'test'" "true"
public interface Test {

}

class Foo {
  void bar() {
    Test test = null;
    test.te<caret>st();
  }
}