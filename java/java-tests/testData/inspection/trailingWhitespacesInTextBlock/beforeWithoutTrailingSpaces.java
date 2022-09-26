// "Fix all 'Trailing whitespace in text block' problems in file" "false"

class Foo {
  void test() {
    String colors = """
      red
      green
<caret> 	""";
  }
}
