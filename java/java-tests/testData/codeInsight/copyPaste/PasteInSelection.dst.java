class A {
  String foo(String s) {
    return s;
  }

  void foo() {
    foo("foo");
    System.out.println(<selection>"hello<caret> world"</selection>);
  }
}