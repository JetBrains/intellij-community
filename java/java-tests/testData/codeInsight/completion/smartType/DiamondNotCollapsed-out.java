class MyClass {
  public void foo() {
    MyDD<String> d = new MyDD<String>(<caret>);
  }
}

class MyDD<T> {
  MyDD(T t){}
}