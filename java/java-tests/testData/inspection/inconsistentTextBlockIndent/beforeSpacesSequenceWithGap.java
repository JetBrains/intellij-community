// "Replace spaces with tabs (4 spaces = 1 tab)" "true"

class Foo {
  void test() {
    String colors = """
   <caret>	 red
			green
        blue""";
  }
}