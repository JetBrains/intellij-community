// "Indent text block with spaces only" "true"

class Foo {
  void test() {
    String colors = """
    red
   <caret>	green
    blue""";

  }
}