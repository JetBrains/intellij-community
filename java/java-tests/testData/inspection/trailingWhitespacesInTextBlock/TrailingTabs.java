class Foo {
  void test() {
    String colors = """
      red<warning descr="Trailing whitespace characters inside text block"><caret>			</warning>
      green
""";
  }
}
