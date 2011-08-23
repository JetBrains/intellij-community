class MyClass {
  public void foo() {
    MyDD<String> d = new MyD<caret>
  }
}

abstract class MyDD<T> {
}