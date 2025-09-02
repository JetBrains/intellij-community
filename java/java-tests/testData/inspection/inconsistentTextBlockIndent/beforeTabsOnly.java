// "Indent text block with spaces only" "false"

class Foo {
  void test() {
    String a = """
					red
					blue
					<caret>  green
					""";
  }
}