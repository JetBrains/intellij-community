// "Fix all 'Trailing whitespaces in text block' problems in file" "false"

class Foo {
  void test() {
    String colors = """
      red
      green
<caret> 	""";
  }
}
