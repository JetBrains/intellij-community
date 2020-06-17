class Foo {
  void foo(MyStream<String> list) {
    list.ma<caret>
  }
}

class MyStream<T> {
  private java.util.stream.Stream<T> stream() {}
}