class Foo {
  void foo(MyList<String> list) {
    list.<caret>
  }
}

class MyList<T> extends java.util.List<T> {
  void filter();
}