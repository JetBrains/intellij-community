// "Replace spaces with tabs (1 space = 1 tab)" "false"

class Foo {
  void test() {
    String a = """
					red
					blue
					<caret>  green
					""";
  }
}