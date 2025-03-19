class Foo {
  void test() {
    String text = """
        hello<warning descr="Trailing whitespace characters inside text block"><caret>      </warning>""";
  }
}
