class Foo {
  void test() {
    String colors = """
      red\040\t<warning descr="Trailing whitespace characters inside text block"><caret> 	 </warning>
      green
""";
  }
}
