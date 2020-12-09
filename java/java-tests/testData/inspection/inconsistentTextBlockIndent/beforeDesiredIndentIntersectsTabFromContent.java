// "Replace tabs with spaces (1 tab = 4 spaces)" "false"

class Foo {
  void test() {
    String colors = """
    red
   <caret>	green
    blue""";

  }
}