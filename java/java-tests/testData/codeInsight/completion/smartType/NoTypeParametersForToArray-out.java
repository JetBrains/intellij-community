class Foo {
  void bar(Object[] o) {}

  Object[] foo() {
    java.util.Set<String> set;
    bar(set.toArray(<caret>));
  }
}