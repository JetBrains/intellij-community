class A {
  String foo(String s) {
    return s;
  }

  void foo() {
    <selection>foo("foo")</selection>;
    System.out.println("hello world");
  }
}