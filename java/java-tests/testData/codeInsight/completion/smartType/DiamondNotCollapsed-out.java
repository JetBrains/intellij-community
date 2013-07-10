class MyClass {
  public void foo() {
    MyDD<String> d = new MyDD<>(<caret>);
  }
}

class MyDD<T> {
  MyDD(T t){}
}